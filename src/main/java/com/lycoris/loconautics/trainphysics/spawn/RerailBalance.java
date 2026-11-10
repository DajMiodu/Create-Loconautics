package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.coupling.CouplerTwist;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.glue.RailGauge;
import com.lycoris.loconautics.trainphysics.glue.RailSnap;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.f;

public final class RerailBalance {

    private RerailBalance() {
    }

    private static final long LOOP_WINDOW_TICKS = 100L;
    private static final int LOOP_LIMIT = 2;
    private static final double MASS_CHANGE = 0.05;
    private static final double CENTER_CHANGE = 0.1;

    private record Snapshot(double mass, Vector3d center) {
        boolean differsFrom(double otherMass, Vector3d otherCenter) {
            return Math.abs(otherMass - mass) > MASS_CHANGE * mass || otherCenter.distance(center) > CENTER_CHANGE;
        }
    }

    private record Balance(double offset, double height, double lift) {
    }

    private static final Map<UUID, Snapshot> TOLD = new ConcurrentHashMap<>();
    private static final Map<UUID, Snapshot> LOOPING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> AUTO_RERAILED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LOOPS = new ConcurrentHashMap<>();

    static boolean refuses(ServerLevel level, ServerSubLevel sub, SableTrain train) {
        MassData md = sub.getMassTracker();
        if (md == null || md.isInvalid()) {
            return false;
        }
        UUID id = train.id();
        double mass = md.getMass();
        Vector3d center = new Vector3d(md.getCenterOfMass());
        Snapshot looping = LOOPING.get(id);
        if (looping != null) {
            if (!looping.differsFrom(mass, center)) {
                BogeyEditTrace.refuse(train, "rerail loop");
                return true;
            }
            forget(id);
        }
        Balance balance = balanceAt(level, sub, train, md);
        if (balance == null || balance.lift() < CarriageRoll.holdAngle()) {
            TOLD.remove(id);
            return false;
        }
        BogeyEditTrace.refuse(train, "too_lopsided");
        Snapshot told = TOLD.get(id);
        if (told == null || told.differsFrom(mass, center)) {
            TOLD.put(id, new Snapshot(mass, center));
            LoconauticsConstants.LOGGER.info(
                    "[wheellift] car={} rerail refused too_lopsided e={} h={} phiEq={} deg hold={} deg coupled={}", id.toString().substring(0, 8),
                    f(balance.offset()), f(balance.height()), f(Math.toDegrees(balance.lift())),
                    f(Math.toDegrees(CarriageRoll.holdAngle())), CouplerTwist.linksOf(sub.getUniqueId()));
            TrainStatusChat.tooLopsided(train);
        }
        return true;
    }

    private static Balance balanceAt(ServerLevel level, ServerSubLevel sub, SableTrain train, MassData md) {
        SableTrain.Car car = train.car();
        RailCarriage carriage = car == null ? null : car.carriage();
        if (carriage == null || carriage.isOnMonorail() || carriage.trackMaterial() == null) {
            return null;
        }
        Vector3d anchor = RailSnap.glueAnchorLocal(sub, car);
        Vector3d forward0 = carriage.referenceForward();
        Vector3d up0 = carriage.referenceUp();
        Vec3 normal = carriage.upNormal();
        Vec3 along = carriage.forward();
        if (anchor == null || forward0 == null || up0 == null || normal == null || along == null
                || along.lengthSqr() < 1.0e-9) {
            return null;
        }
        Vector3d tangent = new Vector3d(along.x, along.y, along.z).normalize();
        if (tangent.dot(SubLevelData.directionOf(sub, forward0)) < 0.0) {
            tangent.negate();
        }
        Vector3d up = new Vector3d(normal.x, normal.y, normal.z);
        up.sub(new Vector3d(tangent).mul(up.dot(tangent)));
        if (up.lengthSquared() < 1.0e-9) {
            return null;
        }
        up.normalize();
        Vector3d lateral = new Vector3d(tangent).cross(up).normalize();
        Vector3d gravity = new Vector3d(DimensionPhysicsData.getGravity(level));
        if (!CarriageRoll.hasGravity(-gravity.dot(up))) {
            return null;
        }
        Vector3d lateral0 = new Vector3d(forward0).cross(up0).normalize();
        Vector3d relative = new Vector3d(md.getCenterOfMass()).sub(anchor).fma(CarriageRoll.SEAT_DROP, up0);
        double offset = relative.dot(lateral0);
        double height = relative.dot(up0);
        double half = RailGauge.halfGauge(carriage.trackMaterial());
        return new Balance(offset, height, CarriageRoll.predictedLift(gravity, up, lateral, half, offset, height));
    }

    static void noteAutoRerail(SableTrain train, long gameTime) {
        AUTO_RERAILED.put(train.id(), gameTime);
    }

    // a hand placed rerail starts the count over
    public static void noteManualRerail(SableTrain train) {
        forget(train.id());
    }

    // the second roll off soon after an auto rerail ends the auto rerails
    public static void noteRolledOff(ServerSubLevel sub, SableTrain train) {
        UUID id = train.id();
        long now = sub.getLevel().getGameTime();
        Long rerailedAt = AUTO_RERAILED.remove(id);
        if (rerailedAt == null || now - rerailedAt > LOOP_WINDOW_TICKS) {
            LOOPS.remove(id);
            return;
        }
        int loops = LOOPS.merge(id, 1, Integer::sum);
        if (loops < LOOP_LIMIT) {
            return;
        }
        MassData md = sub.getMassTracker();
        if (md != null && !md.isInvalid()) {
            LOOPING.put(id, new Snapshot(md.getMass(), new Vector3d(md.getCenterOfMass())));
        }
        RerailApproach.giveUp(train, now);
        LoconauticsConstants.LOGGER.info("[wheellift] car={} rerail loop, giving up", id.toString().substring(0, 8));
    }

    public static void forget(UUID id) {
        TOLD.remove(id);
        LOOPING.remove(id);
        AUTO_RERAILED.remove(id);
        LOOPS.remove(id);
    }

    public static void retain(Set<UUID> live) {
        TOLD.keySet().retainAll(live);
        LOOPING.keySet().retainAll(live);
        AUTO_RERAILED.keySet().retainAll(live);
        LOOPS.keySet().retainAll(live);
    }

    public static void clearAll() {
        TOLD.clear();
        LOOPING.clear();
        AUTO_RERAILED.clear();
        LOOPS.clear();
    }
}
