package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.SeatBlend;
import com.lycoris.loconautics.trainphysics.drive.TrackHostMotion;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;


public final class RailSnap {

    private RailSnap() {
    }

    static final double UNREALIZED_SLIDE_TOLERANCE = 0.02;

    static final java.util.Map<UUID, Double> LAST_SLIDE = new java.util.HashMap<>();

    public static void noteSlide(UUID id, double slide) {
        LAST_SLIDE.put(id, slide);
    }

    public static void retain(java.util.Set<UUID> live) {
        LAST_SLIDE.keySet().retainAll(live);
    }

    public record RailSeat(Vector3d center, Quaterniond orientation, Vector3d localAnchor) {
    }

    public static RailSeat railSeat(ServerSubLevel sub, Car car) {
        Vector3d localAnchor = glueAnchorLocal(sub, car);
        Vector3d[] ref = localAnchor == null ? null : TrainRailAdvance.bogeyReference(sub, car);
        if (ref != null) {
            return new RailSeat(new Vector3d(ref[0]), car.carriage().orientationTo(ref[1]), localAnchor);
        }
        Vector3d center = new Vector3d();
        Quaterniond orientation = SableTrainDriver.bodyPose(car, center);
        return orientation == null ? null : new RailSeat(center, orientation, null);
    }

    public static Vector3d seatPosition(ServerSubLevel sub, RailSeat seat, Quaterniond orientation) {
        return seat.localAnchor() == null
                ? targetPosition(sub, seat.center(), orientation)
                : anchorTo(sub, seat.localAnchor(), seat.center(), orientation);
    }

    static Vector3d targetPosition(ServerSubLevel sub, Vector3d railCenter, Quaterniond orientation) {
        return anchorTo(sub, PoseTweak.anchorCenter(sub), railCenter, orientation);
    }

    static Vector3d anchorTo(ServerSubLevel sub, Vector3dc localAnchor, Vector3d railCenter,
                             Quaterniond orientation) {
        Vector3d offset = new Vector3d(localAnchor).sub(sub.logicalPose().rotationPoint());
        orientation.transform(offset);
        return new Vector3d(railCenter).sub(offset);
    }

    public static Vector3d glueAnchorLocal(ServerSubLevel sub, Car car) {
        if (!SableTrainDriver.useForceGlue(car)) {
            return null;
        }
        Vector3d midSpan = new Vector3d(car.localLead()).add(car.localTrail()).mul(0.5);
        Vector3d bogeyAnchor = TrainRailAdvance.bogeyAnchorLocal(sub, car);
        return bogeyAnchor == null ? midSpan : new Vector3d(bogeyAnchor.x, midSpan.y, bogeyAnchor.z);
    }

    public static void pin(PhysicsPipeline pipeline, ServerSubLevel sub, RailSeat seat, boolean post,
                           Car car, SableTrain train) {
        UUID id = sub.getUniqueId();
        Quaterniond q = SeatBlend.q(id, seat.orientation(), !post);
        PoseTweak.Applied tweaked = PoseTweak.apply(sub, seatPosition(sub, seat, q), q,
                car.carriage().referenceForward(), train.poseTweak());
        q = tweaked.orientation();
        Vector3d target = SeatBlend.pos(id, tweaked.position());
        if (post) {
            Pose3d pose = sub.logicalPose();
            pose.position().set(target);
            pose.orientation().set(q);
        }
        pipeline.teleport(sub, target, q);
        pipeline.resetVelocity(sub);
        com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.holdPivots(sub, train);
        SableTrainDriver.seedRailVelocity(sub, car, train);
    }

    public static void projectVelocityOntoRail(ServerSubLevel sub, Car car, SableTrain train) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        Vector3d tangent = SableTrainDriver.railTangent(car);
        Vector3d vel = handle.getLinearVelocity(new Vector3d());
        Vector3d railVel = SableTrainDriver.railVelocityUnder(sub, car);
        double vTan = new Vector3d(vel).sub(railVel).dot(tangent);
        double capMps = TrainPropulsion.bodySpeedCapMps(train);
        vTan = Math.max(-capMps, Math.min(capMps, vTan));
        Vector3d desired = new Vector3d(tangent).mul(vTan).add(railVel);
        Vector3d deltaLinear = desired.sub(vel, new Vector3d());
        Vector3d angVel = handle.getAngularVelocity(new Vector3d());
        handle.addLinearAndAngularVelocity(deltaLinear, angVel.negate());
    }

    public static void retractUnrealizedSlide(ServerSubLevel sub, Car car, double dt) {
        Double slide = LAST_SLIDE.remove(sub.getUniqueId());
        if (slide == null || Math.abs(slide) < 1.0e-6) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        Vector3d[] ref = TrainRailAdvance.bogeyReference(sub, car);
        if (handle == null || ref == null) {
            return;
        }
        Vector3d relative = handle.getLinearVelocity(new Vector3d()).sub(SableTrainDriver.railVelocityUnder(sub, car));
        double unrealized = (slide - relative.dot(ref[1]) * dt) * Math.signum(slide);
        if (unrealized <= UNREALIZED_SLIDE_TOLERANCE) {
            return;
        }
        TrainRailAdvance.advanceRail(car, -Math.signum(slide) * Math.min(unrealized, Math.abs(slide)),
                TrainRailAdvance.loneFacing(sub, car));
    }

    public static void snapToRail(PhysicsPipeline pipeline, ServerSubLevel sub, Car car, SableTrain train, double dt) {
        retractUnrealizedSlide(sub, car, dt);
        Vector3d[] ref = TrainRailAdvance.bogeyReference(sub, car);
        if (ref == null) {
            projectVelocityOntoRail(sub, car, train);
            return;
        }
        Vector3d railPoint = ref[0];
        Vector3d tangent = ref[1];
        Quaterniond q = car.carriage().orientationTo(tangent);

        Vector3d localAnchor = glueAnchorLocal(sub, car);

        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        Vector3d railVel = SableTrainDriver.railVelocityUnder(sub, car);
        double vTan = 0.0;
        boolean haveVel = false;
        if (handle != null) {
            Vector3d vel = handle.getLinearVelocity(new Vector3d());
            vTan = new Vector3d(vel).sub(railVel).dot(tangent);
            double capMps = TrainPropulsion.bodySpeedCapMps(train);
            vTan = Math.max(-capMps, Math.min(capMps, vTan));
            haveVel = true;
        }

        UUID id = sub.getUniqueId();
        Quaterniond applied = SeatBlend.q(id, q, true);

        Pose3d pose = sub.logicalPose();
        pose.orientation().set(applied);
        Vector3d anchorAfterRot = pose.transformPosition(new Vector3d(localAnchor), new Vector3d());
        Vector3d newPos = new Vector3d(pose.position()).add(railPoint).sub(anchorAfterRot);
        PoseTweak.Applied tweaked = PoseTweak.apply(sub, newPos, applied,
                car.carriage().referenceForward(), train.poseTweak());
        applied = tweaked.orientation();
        newPos = SeatBlend.pos(id, tweaked.position());
        pose.position().set(newPos);
        pose.orientation().set(applied);
        pipeline.teleport(sub, newPos, applied);
        com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.holdPivots(sub, train);

        if (haveVel) {
            Vector3d velNow = handle.getLinearVelocity(new Vector3d());
            Vector3d deltaLinear = new Vector3d(tangent).mul(vTan).add(railVel).sub(velNow);
            Vector3d angVel = handle.getAngularVelocity(new Vector3d());
            handle.addLinearAndAngularVelocity(deltaLinear, angVel.negate());
        }
    }
}
