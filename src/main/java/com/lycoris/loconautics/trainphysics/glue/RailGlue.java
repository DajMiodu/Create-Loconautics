package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.BodyRef;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.f;
import static com.lycoris.loconautics.foundation.LogFormat.shortId;
import static com.lycoris.loconautics.foundation.math.VecMath.perpendicularUp;

public final class RailGlue {

    private RailGlue() {
    }

    public enum GlueMode {
        RIGID, PIVOT, ROLLING, ROLLING_HELD;

        boolean rolls() {
            return this == ROLLING || this == ROLLING_HELD;
        }
    }

    public static final java.util.Map<UUID, GenericConstraintHandle> CONSTRAINTS = new java.util.HashMap<>();
    static final java.util.Map<UUID, GlueMode> GLUE_MODE = new java.util.HashMap<>();
    static final java.util.Map<UUID, Quaterniond> LAST_GLUE_Q = new java.util.HashMap<>();
    static final java.util.Map<UUID, UUID> GLUE_BODY_A_ID = new java.util.HashMap<>();

    static final double MAX_GLUE_STEP = 0.15;

    public static boolean glueBody(PhysicsPipeline pipeline, ServerSubLevel sub, Vector3dc localAnchor,
                                   Vector3d railPoint, Vector3d tangent, Quaterniond q, GlueMode mode,
                                   ServerSubLevel trackSub) {
        return glueBody(pipeline, sub, localAnchor, railPoint, tangent, null, q, mode, trackSub);
    }

    // a null up means the world up, a rolling car passes its track normal so the guide follows a banked rail
    public static boolean glueBody(PhysicsPipeline pipeline, ServerSubLevel sub, Vector3dc localAnchor,
                                   Vector3d railPoint, Vector3d tangent, Vector3d trackUp, Quaterniond q,
                                   GlueMode mode, ServerSubLevel trackSub) {
        UUID id = sub.getUniqueId();
        if (sub.getLevel() instanceof ServerLevel pullLevel && SubLevelMergeWatcher.pulling(pullLevel, id)) {
            releaseConstraint(id);
            return true;
        }
        // a removed track body still arrives as a live reference, and a joint left on it panics rapier
        if (BodyRef.ofBody(trackSub).absent()) {
            releaseConstraint(id);
            return true;
        }
        if (trackSub == null && railInDeadPlot(sub, railPoint)) {
            releaseConstraint(id);
            return false;
        }
        UUID bodyAId = trackSub != null ? trackSub.getUniqueId() : null;

        Vector3d up = trackUp == null ? perpendicularUp(tangent) : new Vector3d(trackUp);
        Vector3d lateral = new Vector3d(tangent).cross(up).normalize();
        Quaterniond frame1World = basisQuaternion(tangent, up, lateral);

        Vector3d pos1;
        Quaterniond frame1;
        PhysicsPipelineBody bodyA;
        if (trackSub != null) {
            pos1 = trackSub.logicalPose().transformPositionInverse(new Vector3d(railPoint), new Vector3d());
            frame1 = new Quaterniond(trackSub.logicalPose().orientation()).conjugate().mul(frame1World);
            bodyA = trackSub;
        } else {
            pos1 = new Vector3d(railPoint);
            frame1 = frame1World;
            bodyA = null;
        }
        Quaterniond frame2 = new Quaterniond(q).invert().mul(frame1World);

        GenericConstraintHandle existing = CONSTRAINTS.get(id);
        if (existing != null && existing.isValid() && GLUE_MODE.get(id) == mode
                && java.util.Objects.equals(GLUE_BODY_A_ID.get(id), bodyAId)) {
            existing.setFrame1(pos1, frame1);
            existing.setFrame2(new Vector3d(localAnchor), frame2);
            return true;
        }
        rebuildGuide(id, mode);
        RigidBodyHandle issue297 = RigidBodyHandle.of(sub);
        Vector3d issue295Anchor = sub.logicalPose().transformPosition(new Vector3d(localAnchor), new Vector3d());
        Quaterniond issue295Turn = new Quaterniond(sub.logicalPose().orientation()).conjugate().mul(q);
        LoconauticsConstants.LOGGER.info(
                "[issue295] new {} glue for {} anchorToRail={} ({} blocks) orientationError={} deg tangent={}", mode,
                shortId(id), new Vector3d(railPoint).sub(issue295Anchor), f(railPoint.distance(issue295Anchor)),
                f(Math.toDegrees(issue295Turn.angle())), tangent);
        LoconauticsConstants.LOGGER.info("[issue297] new {} glue for {} body vel={} ang={} existing={}", mode,
                shortId(id), issue297 == null ? "none" : issue297.getLinearVelocity(new Vector3d()),
                issue297 == null ? "none" : issue297.getAngularVelocity(new Vector3d()), existing != null);
        java.util.Set<ConstraintJointAxis> locked = switch (mode) {
            case PIVOT -> java.util.EnumSet.of(ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z,
                    ConstraintJointAxis.ANGULAR_X);
            case RIGID -> java.util.EnumSet.of(ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z,
                    ConstraintJointAxis.ANGULAR_X, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z);
            case ROLLING -> java.util.EnumSet.of(ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y,
                    ConstraintJointAxis.ANGULAR_Z);
            case ROLLING_HELD -> java.util.EnumSet.of(ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z,
                    ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z);
        };
        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
                pos1, new Vector3d(localAnchor), frame1, frame2, locked);
        GenericConstraintHandle handle = pipeline.addConstraint(bodyA, sub, config);
        if (handle != null) {
            CONSTRAINTS.put(id, handle);
            GLUE_MODE.put(id, mode);
            GLUE_BODY_A_ID.put(id, bodyAId);
        }
        return true;
    }

    // sable refuses to pin a world body to a rail left in a dead plot
    static boolean railInDeadPlot(ServerSubLevel sub, Vector3d railPoint) {
        if (!(sub.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(railPoint);
    }

    public static Quaterniond limitGlueRotation(UUID id, Quaterniond target) {
        Quaterniond last = LAST_GLUE_Q.get(id);
        Quaterniond out;
        if (last == null) {
            out = new Quaterniond(target);
        } else {
            double dot = Math.abs(last.x * target.x + last.y * target.y + last.z * target.z + last.w * target.w);
            double angle = 2.0 * Math.acos(Math.min(1.0, dot));
            if (angle <= MAX_GLUE_STEP || angle < 1.0e-6) {
                out = new Quaterniond(target);
            } else {
                out = new Quaterniond(last).slerp(target, MAX_GLUE_STEP / angle).normalize();
            }
        }
        LAST_GLUE_Q.put(id, out);
        return out;
    }

    public static Quaterniond basisQuaternion(Vector3d forward, Vector3d up, Vector3d lateral) {
        org.joml.Matrix3d m = new org.joml.Matrix3d();
        m.setColumn(0, forward);
        m.setColumn(1, up);
        m.setColumn(2, lateral);
        return m.getNormalizedRotation(new Quaterniond());
    }

    public static String glueModeOf(ServerSubLevel sub) {
        GlueMode mode = sub == null ? null : GLUE_MODE.get(sub.getUniqueId());
        return mode == null ? "none" : mode.name();
    }

    // a rolling guide that only moved to another track body or toggled the staff hold keeps its supports and roll state
    private static void rebuildGuide(UUID id, GlueMode next) {
        GlueMode previous = GLUE_MODE.get(id);
        if (!next.rolls() || previous == null || !previous.rolls()) {
            releaseConstraint(id);
            return;
        }
        GLUE_MODE.remove(id);
        LAST_GLUE_Q.remove(id);
        GLUE_BODY_A_ID.remove(id);
        GenericConstraintHandle handle = CONSTRAINTS.remove(id);
        if (handle != null && handle.isValid()) {
            handle.remove();
        }
    }

    public static void releaseConstraint(UUID id) {
        WheelSupports.release(id);
        CarriageRoll.release(id);
        GLUE_MODE.remove(id);
        LAST_GLUE_Q.remove(id);
        GLUE_BODY_A_ID.remove(id);
        GenericConstraintHandle handle = CONSTRAINTS.remove(id);
        if (handle != null && handle.isValid()) {
            handle.remove();
        }
    }

    // the live ids come back so the caller trims its own per car state to the same set
    public static java.util.Set<UUID> sweepStaleConstraints() {
        java.util.Set<UUID> live = new java.util.HashSet<>();
        java.util.Map<UUID, ServerLevel> levels = new java.util.HashMap<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            UUID id = train.car().subLevelId();
            if (id != null) {
                live.add(id);
                levels.put(id, train.level());
            }
        }
        java.util.List<UUID> stale = new java.util.ArrayList<>();
        for (UUID id : CONSTRAINTS.keySet()) {
            // a live car says nothing about the body it is glued to, and some driver paths never reach gluebody
            if (!live.contains(id) || BodyRef.of(levels.get(id), GLUE_BODY_A_ID.get(id)).absent()) {
                stale.add(id);
            }
        }
        for (UUID id : stale) {
            releaseConstraint(id);
        }
        GLUE_MODE.keySet().retainAll(live);
        LAST_GLUE_Q.keySet().retainAll(live);
        GLUE_BODY_A_ID.keySet().retainAll(live);
        WheelSupports.retain(live);
        CarriageRoll.retain(live);
        com.lycoris.loconautics.trainphysics.debug.RidingBodyTrace.retain(live);
        com.lycoris.loconautics.trainphysics.coupling.CouplerTwist.retain(live);
        return live;
    }

    public static void clearAll() {
        WheelSupports.clearAll();
        CarriageRoll.clearAll();
        com.lycoris.loconautics.trainphysics.coupling.CouplerTwist.clearAll();
        CONSTRAINTS.clear();
        GLUE_MODE.clear();
        LAST_GLUE_Q.clear();
        GLUE_BODY_A_ID.clear();
    }
}
