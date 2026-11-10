package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.glue.RailGlue;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.mixin.sable.physicsstaff.PhysicsStaffDragSessionAccessor;
import com.lycoris.loconautics.mixin.sable.physicsstaff.PhysicsStaffServerHandlerAccessor;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.math.VecMath.perpendicularUp;

public final class ExternalForceDerail {

    private ExternalForceDerail() {
    }

    private static final int LIFT_TICKS = 5;
    private static final double LIFT_RATIO = 100.0;
    private static final double NOISE = 1.0e-6;
    private static final long STALE_TICKS = 1;
    private static final int REGRIP_TICKS = 40;

    private record Push(long gameTime, double lift) {

        Push worse(long now, double otherLift) {
            return gameTime == now ? new Push(now, Math.max(lift, otherLift)) : new Push(now, otherLift);
        }
    }

    private static final Map<UUID, Push> PEAK = new HashMap<>();
    private static final Map<UUID, Integer> LIFTED = new HashMap<>();
    private static final Map<UUID, Long> REGRIP = new HashMap<>();
    private static final Map<UUID, Long> RELEASED = new HashMap<>();
    private static final int RELEASE_TICKS = 10;
    private static final double RELEASE_SPEED = 4.0;
    private static final double RELEASE_SPIN = 2.0;

    static void sample(ServerSubLevel sub, Car car, SableTrain train, double dt) {
        if (!LoconauticsConfig.DERAIL_ON_EXTERNAL_FORCE.get() || dt <= 0.0 || TrainPropulsion.isMonorail(train)) {
            return;
        }
        long now = sub.getLevel().getGameTime();
        boolean dragged = staffDrags(sub);
        if (SubLevelMergeWatcher.merging(sub.getLevel(), sub.getUniqueId())) {
            PEAK.remove(sub.getUniqueId());
            return;
        }
        if (staffHolds(sub) && !dragged) {
            forget(train.id());
            PEAK.remove(sub.getUniqueId());
            return;
        }
        MassData mass = sub.getMassTracker();
        if (mass == null || mass.isInvalid() || mass.getMass() <= 0.0) {
            return;
        }
        Vector3d[] ref = TrainRailAdvance.bogeyReference(car);
        Vector3d tangent = ref != null ? ref[1] : SableTrainDriver.railTangent(car);
        if (tangent == null) {
            return;
        }
        Vector3d up = perpendicularUp(tangent);

        double lift = 0.0;
        Vector3d push = queuedPush(sub);
        if (push != null) {
            Vector3d accel = new Vector3d(push).mul(1.0 / (mass.getMass() * dt));
            lift = accel.dot(up);
        }

        Vector3d held = train.hasOffTrackBogey() ? null : jointAccel(sub, tangent, up, mass.getMass(), dt);
        double pull = held == null ? 0.0 : held.dot(up);
        lift = Math.max(lift, pull);
        if (lift == 0.0) {
            return;
        }

        double gravity = DimensionPhysicsData.getGravity(sub.getLevel()).length();
        if (lift > gravity * LIFT_RATIO) {
            REGRIP.put(train.id(), now + REGRIP_TICKS);
        }
        Push seen = PEAK.get(sub.getUniqueId());
        PEAK.put(sub.getUniqueId(), seen == null ? new Push(now, lift) : seen.worse(now, lift));
    }

    private static Vector3d queuedPush(ServerSubLevel sub) {
        Map<ForceGroup, QueuedForceGroup> groups = sub.getQueuedForceGroups();
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        Vector3d local = new Vector3d();
        for (QueuedForceGroup group : groups.values()) {
            local.add(group.getForceTotal().getLocalForce());
        }
        if (local.lengthSquared() < NOISE) {
            return null;
        }
        return sub.logicalPose().transformNormal(local, new Vector3d());
    }

    private static Vector3d jointAccel(ServerSubLevel sub, Vector3d tangent, Vector3d up, double mass, double dt) {
        GenericConstraintHandle glue = RailGlue.CONSTRAINTS.get(sub.getUniqueId());
        if (glue == null || !glue.isValid()) {
            return null;
        }
        Vector3d linear = new Vector3d();
        glue.getJointImpulses(linear, new Vector3d());
        Vector3d lateral = new Vector3d(tangent).cross(up).normalize();
        return RailGlue.basisQuaternion(tangent, up, lateral).transform(linear, new Vector3d())
                .mul(1.0 / (mass * dt));
    }

    public static void dampRelease(ServerSubLevel sub, SableTrain train) {
        Long until = RELEASED.get(train.id());
        if (until == null) {
            return;
        }
        if (sub.getLevel().getGameTime() > until) {
            RELEASED.remove(train.id());
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        Vector3d slowLinear = overspill(handle.getLinearVelocity(new Vector3d()), RELEASE_SPEED);
        Vector3d slowAngular = overspill(handle.getAngularVelocity(new Vector3d()), RELEASE_SPIN);
        if (slowLinear.lengthSquared() > NOISE || slowAngular.lengthSquared() > NOISE) {
            handle.addLinearAndAngularVelocity(slowLinear, slowAngular);
        }
    }

    private static Vector3d overspill(Vector3d velocity, double limit) {
        double speed = velocity.length();
        return speed <= limit ? new Vector3d() : velocity.mul(limit / speed - 1.0);
    }

    static boolean staffDrags(ServerSubLevel sub) {
        if (!(sub.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        try {
            PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(level);
            for (Object session : ((PhysicsStaffServerHandlerAccessor) handler).loconautics$getDraggingSessions().values()) {
                if (((PhysicsStaffDragSessionAccessor) session).loconautics$getSubLevel() == sub) {
                    return true;
                }
            }
            return false;
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    public static boolean staffLocked(ServerSubLevel sub) {
        if (!(sub.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        try {
            return PhysicsStaffServerHandler.get(level).isLocked(sub);
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    public static boolean staffHolds(ServerSubLevel sub) {
        if (!(sub.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        try {
            return PhysicsStaffServerHandler.get(level).isLocked(sub) || staffDrags(sub);
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    public static boolean staffLockedById(ServerLevel level, Collection<UUID> ids) {
        try {
            Map<UUID, Object> locks = ((PhysicsStaffServerHandlerAccessor) PhysicsStaffServerHandler.get(level)).loconautics$getLocks();
            for (UUID id : ids) {
                if (locks.containsKey(id)) {
                    return true;
                }
            }
            return false;
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    public static boolean holdsRerail(SableTrain train) {
        Long until = REGRIP.get(train.id());
        return until != null && train.level() != null && train.level().getGameTime() < until;
    }

    static boolean check(SableTrain train, ServerSubLevel sub) {
        Push push = PEAK.remove(sub.getUniqueId());
        if (push == null || TrainPropulsion.isMonorail(train)
                || sub.getLevel().getGameTime() - push.gameTime() > STALE_TICKS) {
            forget(train.id());
            return false;
        }
        double gravity = DimensionPhysicsData.getGravity(sub.getLevel()).length();
        return checkLift(train, push, gravity * LIFT_RATIO);
    }

    private static boolean checkLift(SableTrain train, Push push, double limit) {
        if (push.lift() <= limit) {
            LIFTED.remove(train.id());
            return false;
        }
        if (LIFTED.merge(train.id(), 1, Integer::sum) < LIFT_TICKS) {
            return false;
        }
        forget(train.id());
        train.setDerailed(true, "lifted_off");
        if (train.level() != null) {
            RELEASED.put(train.id(), train.level().getGameTime() + RELEASE_TICKS);
        }
        return true;
    }

    private static void forget(UUID trainId) {
        LIFTED.remove(trainId);
    }

    static void retain(Set<UUID> liveTrains, Set<UUID> liveSubLevels) {
        PEAK.keySet().retainAll(liveSubLevels);
        LIFTED.keySet().retainAll(liveTrains);
        REGRIP.keySet().retainAll(liveTrains);
        RELEASED.keySet().retainAll(liveTrains);
    }
}
