package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.teleport.TrainTeleporter;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrainPortalCrossing {

    private static final double HANDOVER_DEPTH = 0.25;

    private static final double CLEAR_MARGIN = 1.0;

    private static final long RETRY_TICKS = 40;

    private static final Map<UUID, PortalFrame> STRADDLING = new HashMap<>();

    private static final Map<UUID, Long> RETRY_AT = new HashMap<>();

    private static final Map<UUID, Integer> CLEAR_TICKS = new HashMap<>();

    private static final int CLEAR_HOLD = 20;

    private static final Map<UUID, Long> ISSUE278_UNTIL = new HashMap<>();

    private static long JAM_LAST_NANOS = System.nanoTime();

    private static long JAM_LAST_GC;

    private TrainPortalCrossing() {
    }

    public static Set<UUID> entering() {
        return Set.copyOf(STRADDLING.keySet());
    }

    public static boolean isCrossing(UUID subLevelId) {
        return SableTeleportation.isInFlight(subLevelId);
    }

    public static PortalFrame straddle(UUID carSubLevelId) {
        return STRADDLING.get(carSubLevelId);
    }

    public static void tick(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car == null) {
            return;
        }
        issue278After(train, car);
        PortalFrame frame = unifyFrame(car, train);
        if (frame == null) {
            end(train, false);
            return;
        }
        ServerLevel home = train.level();
        ServerSubLevel master = SubLevelData.resolve(home, car.subLevelId());
        if (master == null) {
            return;
        }
        double[] span = span(master, frame);
        if (span == null) {
            return;
        }
        if (!TrainRailAdvance.anyPointFar(car) && span[1] <= -CLEAR_MARGIN) {
            int clear = CLEAR_TICKS.merge(car.subLevelId(), 1, Integer::sum);
            if (clear >= CLEAR_HOLD) {
                CLEAR_TICKS.remove(car.subLevelId());
                end(train, true);
                return;
            }
        } else {
            CLEAR_TICKS.remove(car.subLevelId());
        }
        STRADDLING.put(car.subLevelId(), frame);
        PortalHold.hold(home, car.subLevelId());
        // twins first, a handover on the first straddle tick would otherwise skip the standby and pop the whole car over
        long straddleStart = System.nanoTime();
        straddle(train, car, master, frame);
        long straddleMicros = (System.nanoTime() - straddleStart) / 1_000L;
        if (PortalTrace.dueAlways("longtrain" + car.subLevelId(), train.level().getGameTime(), 20)) {
            LoconauticsConstants.LOGGER.info("[longtrain] tick car={} chain={} straddling={} straddleMicros={} {}",
                    PortalTrace.id(car.subLevelId()), chain(train).size(), STRADDLING.size(), straddleMicros,
                    PortalTwins.debugCounts());
        }
        traceJam(train, car, master, frame);
        if (handOver(train, frame)) {
            return;
        }
    }

    private static void traceJam(SableTrain train, SableTrain.Car car, ServerSubLevel master, PortalFrame frame) {
        long now = train.level().getGameTime();
        if (!PortalTrace.dueAlways("xjam" + car.subLevelId(), now, 2)) {
            return;
        }
        var container = SubLevelContainer.getContainer(train.level());
        Vector3d velocity = container == null ? new Vector3d()
                : container.physicsSystem().getPipeline().getLinearVelocity(master, new Vector3d());
        StringBuilder points = new StringBuilder();
        for (RailCarriage rail : rails(car)) {
            points.append(rail.isFar(rail.leading()) ? 'F' : 'n').append(rail.isFar(rail.trailing()) ? 'F' : 'n')
                    .append(rail.reachedEnd() ? 'E' : '-').append(' ');
        }
        long nanos = System.nanoTime();
        long gcMs = 0L;
        for (var bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            gcMs += Math.max(0L, bean.getCollectionTime());
        }
        long wallMs = (nanos - JAM_LAST_NANOS) / 1_000_000L;
        long gcDelta = gcMs - JAM_LAST_GC;
        JAM_LAST_NANOS = nanos;
        JAM_LAST_GC = gcMs;
        Runtime runtime = Runtime.getRuntime();
        LoconauticsConstants.LOGGER.info("[xjam] wallMs={} gcMs={} heapMb={} car={} tick={} depth={} trainV={} target={} "
                        + "bodyV={} derailed={} settle={} rails={}", wallMs, gcDelta,
                (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L, PortalTrace.id(car.subLevelId()), now,
                PortalTrace.f(frame.depth(master.logicalPose().position())), PortalTrace.f(train.speed()),
                PortalTrace.f(train.targetSpeed()), PortalTrace.f(velocity.length()), train.isDerailed(),
                train.isPoseSettling(), points);
    }

    private static void straddle(SableTrain train, SableTrain.Car car, ServerSubLevel master, PortalFrame frame) {
        MinecraftServer server = train.level().getServer();
        ServerLevel farLevel = server.getLevel(frame.farDim());
        Vector3d farCenter = frame.toFar(master.logicalPose().position());
        if (farLevel != null && !PortalHold.holdFarChunks(farLevel, farCenter.x, farCenter.y, farCenter.z)) {
            return;
        }
        Set<UUID> group = new HashSet<>();
        for (SableTrain member : chain(train)) {
            group.add(member.car().subLevelId());
        }
        List<UUID> homeBodies = new ArrayList<>();
        List<UUID> twinBodies = new ArrayList<>();
        List<UUID> standbyBodies = new ArrayList<>();
        homeBodies.add(car.subLevelId());
        ServerSubLevel twin = PortalTwins.ensure(master, frame, car.subLevelId(), group);
        if (twin != null) {
            twinBodies.add(twin.getUniqueId());
        }
        for (UUID plateId : GhostWheelBodies.wheelBodiesOf(car.subLevelId())) {
            ServerSubLevel plate = SubLevelData.resolve(train.level(), plateId);
            if (plate == null) {
                continue;
            }
            homeBodies.add(plateId);
            ServerSubLevel plateTwin = PortalTwins.ensure(plate, frame, car.subLevelId(), group);
            if (plateTwin != null) {
                twinBodies.add(plateTwin.getUniqueId());
            }
        }
        PortalTwins.retain(server, car.subLevelId(), homeBodies);
        UUID standby = PortalTwins.standbyBody(car.subLevelId());
        if (standby != null) {
            standbyBodies.add(standby);
        }
        PortalEntry.sync(train.level(), car.subLevelId(), frame, homeBodies, standbyBodies,
                server.getLevel(frame.farDim()), twinBodies);
        if (PortalTrace.due(car.subLevelId(), train.level().getGameTime(), 20)) {
            double[] span = span(master, frame);
            PortalTrace.log("straddle car={} {} to {} span={} to {} home={} plates={} twins={} standby={} "
                            + "twinOfCar={} anyPointFar={}", PortalTrace.id(car.subLevelId()),
                    frame.nearDim().location(), frame.farDim().location(),
                    span == null ? "n/a" : PortalTrace.f(span[0]), span == null ? "n/a" : PortalTrace.f(span[1]),
                    homeBodies.size(), homeBodies.size() - 1, twinBodies.size(), standbyBodies.size(),
                    twin != null, TrainRailAdvance.anyPointFar(car));
        }
    }

    private static void issue286(SableTrain train, String why) {
        if (PortalTrace.dueAlways("issue286" + train.car().subLevelId() + why, train.level().getGameTime(), 40)) {
            LoconauticsConstants.LOGGER.info("[issue286] car={} dim={} speed={} physics={} {}",
                    PortalTrace.id(train.car().subLevelId()), train.level().dimension().location(),
                    PortalTrace.f(train.speed()), train.isPhysics(), why);
        }
    }

    private static boolean handOver(SableTrain train, PortalFrame frame) {
        List<SableTrain> chain = chain(train);
        List<SableTrain> past = new ArrayList<>();
        List<ServerPlayer> riders = new ArrayList<>();
        for (SableTrain member : chain) {
            ServerSubLevel sub = member.level() == train.level()
                    ? SubLevelData.resolve(member.level(), member.car().subLevelId()) : null;
            if (sub == null) {
                issue286(train, "no live body for car " + PortalTrace.id(member.car().subLevelId()) + " on this side");
                return false;
            }
            riders.addAll(TrainAdvancements.riders(sub));
            if (frame.depth(sub.logicalPose().position()) > HANDOVER_DEPTH) {
                past.add(member);
            }
        }
        long now = train.level().getGameTime();
        if (past.size() * 2 <= chain.size()) {
            issue286(train, "waiting, only " + past.size() + " of " + chain.size() + " cars are past the plane");
            return false;
        }
        long retryAt = RETRY_AT.getOrDefault(train.car().subLevelId(), 0L);
        if (now < retryAt) {
            issue286(train, "backing off after a failed handover for " + (retryAt - now) + " more ticks");
            return false;
        }
        String refusal = TrainTeleporter.handOverRefusal(train);
        if (refusal != null) {
            issue286(train, "refused: " + refusal);
            if (PortalTrace.due("refusal" + train.car().subLevelId(), now, 20)) {
                PortalTrace.log("handover refused car={} why={}", PortalTrace.id(train.car().subLevelId()), refusal);
            }
            return false;
        }
        MinecraftServer server = train.level().getServer();
        boolean waiting = false;
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            waiting |= PortalTwins.standbyPending(SubLevelData.resolve(member.level(), id), id);
        }
        if (waiting) {
            issue286(train, "waiting on a twin standby body");
            if (PortalTrace.due("waiting" + train.car().subLevelId(), now, 5)) {
                PortalTrace.log("handover waiting on standby car={}", PortalTrace.id(train.car().subLevelId()));
            }
            return false;
        }
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            PortalTwins.softenTwin(server, id);
            PortalHold.release(server, id);
        }
        issue278Before(chain, frame, past);
        long handStarted = System.nanoTime();
        TrainTeleporter.Result result = TrainTeleporter.handOver(train, frame,
                ignored -> { });
        long handMs = (System.nanoTime() - handStarted) / 1_000_000L;
        if (handMs >= 40) {
            LoconauticsConstants.LOGGER.warn("[portal] slow handover of {} took {}ms", PortalTrace.id(train.car().subLevelId()),
                    handMs);
        }
        PortalTrace.log("handover result car={} ok={} moved={}", PortalTrace.id(train.car().subLevelId()),
                result.ok(), result.moved());
        LoconauticsConstants.LOGGER.info("[longtrain] handover car={} chain={} past={} ok={} moved={} ms={} {}",
                PortalTrace.id(train.car().subLevelId()), chain.size(), past.size(), result.ok(), result.moved(),
                handMs, PortalTwins.debugCounts());
        if (!result.ok()) {
            LoconauticsConstants.LOGGER.warn("[issue286] [issue281] handover failed car={} chain={} moved={} why={}",
                    PortalTrace.id(train.car().subLevelId()), chain.size(), result.moved(), result.failure());
            RETRY_AT.put(train.car().subLevelId(), now + RETRY_TICKS);
            return false;
        }
        PortalFrame turned = frame.inverted();
        for (SableTrain member : chain) {
            ISSUE278_UNTIL.put(member.car().subLevelId(), now + 100);
        }
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            PortalEntry.clear(id, PortalTwins.handedOver(server, id, turned));
            STRADDLING.remove(id);
        }
        RETRY_AT.remove(train.car().subLevelId());
        TrainAdvancements.portal(riders);
        LoconauticsConstants.LOGGER.info("[portal] handed {} car(s) from {} to {}", result.moved(),
                frame.nearDim().location(), frame.farDim().location());
        return true;
    }

    private static void issue278Before(List<SableTrain> chain, PortalFrame frame, List<SableTrain> past) {
        StringBuilder out = new StringBuilder();
        for (SableTrain member : chain) {
            ServerSubLevel sub = SubLevelData.resolve(member.level(), member.car().subLevelId());
            out.append(PortalTrace.id(member.car().subLevelId())).append(" depth=")
                    .append(sub == null ? "none" : PortalTrace.f(frame.depth(sub.logicalPose().position())))
                    .append(" past=").append(past.contains(member)).append(" far=")
                    .append(TrainRailAdvance.anyPointFar(member.car())).append(" physics=").append(member.isPhysics())
                    .append(" derailed=").append(member.isDerailed()).append(" speed=")
                    .append(PortalTrace.f(member.speed())).append(" | ");
        }
        LoconauticsConstants.LOGGER.info("[issue278] handover chain={} past={} cars: {}", chain.size(), past.size(), out);
    }

    private static void issue278After(SableTrain train, SableTrain.Car car) {
        Long until = ISSUE278_UNTIL.get(car.subLevelId());
        if (until == null) {
            return;
        }
        long now = train.level().getGameTime();
        if (now > until) {
            ISSUE278_UNTIL.remove(car.subLevelId());
            return;
        }
        if (!PortalTrace.dueAlways("issue278" + car.subLevelId(), now, 10)) {
            return;
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), car.subLevelId());
        PortalFrame own = rails(car).stream().map(RailCarriage::portal).filter(f -> f != null).findFirst().orElse(null);
        StringBuilder rails = new StringBuilder();
        for (RailCarriage rail : rails(car)) {
            rails.append(rail.isFar(rail.leading()) ? 'F' : 'n').append(rail.isFar(rail.trailing()) ? 'F' : 'n')
                    .append(rail.reachedEnd() ? 'E' : '-').append(' ');
        }
        LoconauticsConstants.LOGGER.info("[issue278] after car={} level={} body={} depthInOwnFrame={} frame={} rails={} "
                        + "derailed={} physics={} settle={} speed={} partners={}", PortalTrace.id(car.subLevelId()),
                train.level().dimension().location(), sub != null,
                sub == null || own == null ? "n/a" : PortalTrace.f(own.depth(sub.logicalPose().position())),
                own != null, rails, train.isDerailed(), train.isPhysics(), train.isPoseSettling(),
                PortalTrace.f(train.speed()), chain(train).size());
    }

    // a body overhangs its bogeys, so the straddle has to start when its front nears the plane, not when a bogey does
    private static PortalFrame lookAhead(SableTrain.Car car, SableTrain train) {
        ServerSubLevel master = SubLevelData.resolve(train.level(), car.subLevelId());
        BoundingBox3ic bounds = master == null ? null : master.getPlot().getBoundingBox();
        if (car.carriage() == null || bounds == null || bounds.volume() <= 0) {
            return null;
        }
        double reach = Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) + 1.0;
        PortalFrame ahead = car.carriage().portalWithin(reach);
        double[] span = ahead == null ? null : span(master, ahead);
        if (span == null || span[1] <= -CLEAR_MARGIN) {
            return null;
        }
        return ahead;
    }

    private static PortalFrame unifyFrame(SableTrain.Car car, SableTrain train) {
        PortalFrame frame = rails(car).stream().map(RailCarriage::portal)
                .filter(own -> own != null && own.nearDim().equals(train.level().dimension()))
                .findFirst().orElse(null);
        if (frame == null) {
            frame = lookAhead(car, train);
        }
        if (frame == null) {
            return null;
        }
        for (RailCarriage rail : rails(car)) {
            if (rail.portal() != frame) {
                rail.setPortal(frame);
            }
        }
        return frame;
    }

    private static void end(SableTrain train, boolean clearRails) {
        SableTrain.Car car = train.car();
        UUID id = car.subLevelId();
        if (clearRails) {
            for (RailCarriage rail : rails(car)) {
                rail.clearPortal();
            }
        }
        RETRY_AT.remove(id);
        CLEAR_TICKS.remove(id);
        if (STRADDLING.remove(id) == null) {
            return;
        }
        PortalTrace.log("end straddle car={} clearRails={}", PortalTrace.id(id), clearRails);
        MinecraftServer server = train.level().getServer();
        PortalTwins.endStraddle(server, id);
        PortalEntry.clear(id);
        PortalHold.release(server, id);
    }

    private static List<RailCarriage> rails(SableTrain.Car car) {
        List<RailCarriage> rails = new ArrayList<>();
        if (car.carriage() != null) {
            rails.add(car.carriage());
        }
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (bogey.rail() != null) {
                rails.add(bogey.rail());
            }
        }
        return rails;
    }

    private static List<SableTrain> chain(SableTrain train) {
        List<SableTrain> cars = SableTrainSpawner.chainCars(train.level(), train.car().subLevelId());
        return cars.isEmpty() ? List.of(train) : cars;
    }

    private static double[] span(ServerSubLevel sub, PortalFrame frame) {
        BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
        if (bounds == null || bounds.volume() <= 0) {
            return null;
        }
        Pose3dc pose = sub.logicalPose();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        Vector3d corner = new Vector3d();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? bounds.minX() : bounds.maxX() + 1.0,
                    (i & 2) == 0 ? bounds.minY() : bounds.maxY() + 1.0,
                    (i & 4) == 0 ? bounds.minZ() : bounds.maxZ() + 1.0);
            pose.transformPosition(corner);
            double depth = frame.depth(corner);
            min = Math.min(min, depth);
            max = Math.max(max, depth);
        }
        return new double[] {min, max};
    }
}
