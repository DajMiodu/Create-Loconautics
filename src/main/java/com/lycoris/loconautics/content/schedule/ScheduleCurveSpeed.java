package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.track.BezierConnection;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.f;
import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class ScheduleCurveSpeed {

    private static final long REFRESH_TICKS = 10L;
    private static final double SAFE_SHARE = 0.5;
    private static final double BRAKE_SHARE = 0.8;
    private static final double LOOK_MARGIN = 8.0;
    private static final int CURVE_SAMPLES = 16;
    private static final double TICKS_PER_SECOND = 20.0;

    private record Rated(UUID car, CarriageRoll.Roll roll) {
    }

    private static final class Plan {
        long refreshed = Long.MIN_VALUE / 2;
        List<Rated> cars = List.of();
        double gN;
        double aheadCap = Double.POSITIVE_INFINITY;
        double aheadAt;
        double travelled;
        double lastCap = Double.NaN;
    }

    private static final Map<UUID, Plan> PLANS = new ConcurrentHashMap<>();
    private static final Map<BezierConnection, Double> CURVATURE = new WeakHashMap<>();

    private ScheduleCurveSpeed() {
    }

    // blocks per tick the consist may run at right now, infinite when no curve needs it
    public static double allowedBpt(SableTrain train, TravellingPoint nose, TrackGraph graph,
                                    List<Couple<TrackNode>> route, double decelBpt2) {
        if (!LoconauticsConfig.DERAIL_ON_CURVE.get() || train.isGhost() || !(train.level() instanceof ServerLevel level)
                || train.car() == null) {
            PLANS.remove(train.id());
            return Double.POSITIVE_INFINITY;
        }
        Plan plan = PLANS.computeIfAbsent(train.id(), key -> new Plan());
        long now = level.getGameTime();
        double speed = Math.abs(train.speed());
        double decel = Math.max(1.0e-4, decelBpt2 * BRAKE_SHARE);
        if (now - plan.refreshed >= REFRESH_TICKS) {
            refresh(plan, level, train, nose, graph, route, speed, decel, now);
        } else {
            plan.travelled += speed;
        }
        double allowed = onCurveCap(plan);
        if (Double.isFinite(plan.aheadCap)) {
            double room = Math.max(0.0, plan.aheadAt - plan.travelled);
            allowed = Math.min(allowed, Math.sqrt(plan.aheadCap * plan.aheadCap + 2.0 * decel * room));
        }
        noteChange(plan, train, allowed, speed);
        return allowed;
    }

    private static void refresh(Plan plan, ServerLevel level, SableTrain train, TravellingPoint nose, TrackGraph graph,
                                List<Couple<TrackNode>> route, double speed, double decel, long now) {
        plan.refreshed = now;
        plan.travelled = 0.0;
        PLANS.keySet().removeIf(id -> SableTrainRegistry.get(id) == null);
        plan.gN = DimensionPhysicsData.getGravity(level).length();
        List<Rated> cars = new ArrayList<>();
        for (UUID id : TrainConsist.orderedConsist(level, train.car().subLevelId())) {
            CarriageRoll.Roll roll = CarriageRoll.rollById(id);
            if (roll != null && roll.rolling && roll.half > 0.0) {
                cars.add(new Rated(id, roll));
            }
        }
        plan.cars = cars;
        plan.aheadCap = Double.POSITIVE_INFINITY;
        plan.aheadAt = 0.0;
        if (cars.isEmpty()) {
            return;
        }
        double horizon = speed * speed / (2.0 * decel) + LOOK_MARGIN;
        double[] best = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0};
        RouteDistance.walkAhead(nose, graph, route, horizon, (edge, startsAt) -> {
            double cap = capFor(plan, curvatureOf(edge));
            double envelope = Math.sqrt(cap * cap + 2.0 * decel * startsAt);
            if (envelope < best[0]) {
                best[0] = envelope;
                best[1] = cap;
                best[2] = startsAt;
            }
        });
        plan.aheadCap = best[1];
        plan.aheadAt = best[2];
    }

    // the tail is still on the curve after the nose leaves it
    private static double onCurveCap(Plan plan) {
        double cap = Double.POSITIVE_INFINITY;
        for (Rated rated : plan.cars) {
            SableTrain car = SableTrainRegistry.bySubLevel(rated.car());
            if (car == null || car.car() == null) {
                continue;
            }
            for (SableTrain.Bogey bogey : car.car().bogeys()) {
                RailCarriage rail = bogey.rail();
                if (rail == null) {
                    continue;
                }
                cap = Math.min(cap, capFor(plan, curvatureOf(rail.leading())));
                cap = Math.min(cap, capFor(plan, curvatureOf(rail.trailing())));
            }
        }
        return cap;
    }

    private static double capFor(Plan plan, double curvature) {
        if (curvature <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double cap = Double.POSITIVE_INFINITY;
        for (Rated rated : plan.cars) {
            cap = Math.min(cap, CarriageRoll.safeCurveSpeed(rated.roll(), plan.gN, curvature, SAFE_SHARE));
        }
        return cap / TICKS_PER_SECOND;
    }

    private static double curvatureOf(TravellingPoint point) {
        return point == null ? 0.0 : curvatureOf(point.edge);
    }

    private static double curvatureOf(TrackEdge edge) {
        if (edge == null || !edge.isTurn() || edge.getTurn() == null) {
            return 0.0;
        }
        return CURVATURE.computeIfAbsent(edge.getTurn(), ScheduleCurveSpeed::sharpest);
    }

    // turn of the flat heading per block between samples, the worst span wins
    private static double sharpest(BezierConnection turn) {
        double worst = 0.0;
        Vec3 previous = null;
        Vec3 last = turn.getPosition(0.0);
        for (int i = 1; i <= CURVE_SAMPLES; i++) {
            Vec3 point = turn.getPosition((double) i / CURVE_SAMPLES);
            Vec3 step = new Vec3(point.x - last.x, 0.0, point.z - last.z);
            double length = step.length();
            if (length > 1.0e-6) {
                Vec3 heading = step.scale(1.0 / length);
                if (previous != null) {
                    double angle = Math.acos(Math.max(-1.0, Math.min(1.0, previous.dot(heading))));
                    worst = Math.max(worst, angle / length);
                }
                previous = heading;
            }
            last = point;
        }
        return worst;
    }

    private static void noteChange(Plan plan, SableTrain train, double allowed, double speed) {
        double shown = Double.isFinite(allowed) ? Math.round(allowed * TICKS_PER_SECOND) : Double.POSITIVE_INFINITY;
        if (Double.compare(shown, plan.lastCap) == 0) {
            return;
        }
        plan.lastCap = shown;
        LoconauticsConstants.LOGGER.info(
                "[curvespeed] train={} allowed={} b/s speed={} b/s ahead cap={} b/s in {} blocks cars={}",
                shortId(train.id()), Double.isFinite(allowed) ? f(allowed * TICKS_PER_SECOND) : "none",
                f(speed * TICKS_PER_SECOND),
                Double.isFinite(plan.aheadCap) ? f(plan.aheadCap * TICKS_PER_SECOND) : "none",
                f(plan.aheadAt - plan.travelled), plan.cars.size());
    }
}
