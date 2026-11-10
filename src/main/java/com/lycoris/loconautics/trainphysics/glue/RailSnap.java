package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.SeatBlend;
import com.lycoris.loconautics.trainphysics.drive.TrackHostMotion;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.spawn.RerailBalance;
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

import static com.lycoris.loconautics.foundation.LogFormat.f;
import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class RailSnap {

    private RailSnap() {
    }

    static final double UNREALIZED_SLIDE_TOLERANCE = 0.02;
    private static final double RISE_EPS = 1.0e-3;
    private static final double SINK_LOG = 0.02;
    private static final double KICK_ACCEL = 20.0;
    private static final double KICK_LOG = 0.5;
    private static final double CURVE_CLAMP_MARGIN = Math.toRadians(2.0);

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
        if (ForceTrace.enabled()) {
            RigidBodyHandle handle = RigidBodyHandle.of(sub);
            Vector3d moved = new Vector3d(target).sub(sub.logicalPose().position());
            ForceTrace.deltaV(sub, ForceSource.RAIL_PIN,
                    handle == null ? new Vector3d() : handle.getLinearVelocity(new Vector3d()).negate(), target,
                    (post ? "post" : "pre") + " teleport moved=" + f(moved.length()) + " and wiped the velocity");
        }
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
        ForceTrace.deltaV(sub, ForceSource.RAIL_SNAP, deltaLinear, new Vector3d(sub.logicalPose().position()),
                "no bogey reference, velocity projected onto the rail and the spin wiped");
    }

    // a body stopped by a wall never moved the full slide, snapping to the cursor would push it in
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
        boolean rolling = CarriageRoll.consumeRolling(sub.getUniqueId());
        Vector3d[] ref = TrainRailAdvance.bogeyReference(sub, car);
        if (ref == null) {
            projectVelocityOntoRail(sub, car, train);
            return;
        }
        if (rolling && snapRolling(pipeline, sub, car, train, ref, dt)) {
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
        // a glued car never reaches pin, so a spawn or relocate seat has to be walked out here instead
        Quaterniond applied = SeatBlend.q(id, q, true);

        Pose3d pose = sub.logicalPose();
        Vector3d poseBeforeSnap = new Vector3d(pose.position());
        pose.orientation().set(applied);
        Vector3d anchorAfterRot = pose.transformPosition(new Vector3d(localAnchor), new Vector3d());
        Vector3d newPos = new Vector3d(pose.position()).add(railPoint).sub(anchorAfterRot);
        PoseTweak.Applied tweaked = PoseTweak.apply(sub, newPos, applied,
                car.carriage().referenceForward(), train.poseTweak());
        applied = tweaked.orientation();
        newPos = SeatBlend.pos(id, tweaked.position());
        pose.position().set(newPos);
        pose.orientation().set(applied);
        com.lycoris.loconautics.common.strand.RopePullTrace.noteSnap(sub, newPos.distance(poseBeforeSnap));
        pipeline.teleport(sub, newPos, applied);
        com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.holdPivots(sub, train);
        com.lycoris.loconautics.trainphysics.debug.RidingBodyTrace.trace(sub, RailGlue.glueModeOf(sub), 0.0,
                sub.getLevel().getGameTime());

        if (haveVel) {
            Vector3d velNow = handle.getLinearVelocity(new Vector3d());
            Vector3d deltaLinear = new Vector3d(tangent).mul(vTan).add(railVel).sub(velNow);
            Vector3d angVel = handle.getAngularVelocity(new Vector3d());
            handle.addLinearAndAngularVelocity(deltaLinear, angVel.negate());
            ForceTrace.deltaV(sub, ForceSource.RAIL_SNAP, deltaLinear, railPoint,
                    "post step snap, vTan=" + f(vTan) + " spin wiped " + f(angVel.length()) + " rad/s");
        }
    }

    // pivots on the hinge rail, only yaw and pitch snap rigid
    static boolean snapRolling(PhysicsPipeline pipeline, ServerSubLevel sub, Car car, SableTrain train,
                               Vector3d[] ref, double dt) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        MassData md = sub.getMassTracker();
        RollFrame frame = RollFrame.of(sub, car, ref);
        if (handle == null || md == null || md.isInvalid() || frame == null) {
            return false;
        }
        UUID id = sub.getUniqueId();
        CarriageRoll.Roll roll = CarriageRoll.of(id);
        long now = sub.getLevel().getGameTime();
        boolean settling = roll.settling(now) || SeatBlend.active(id);
        PoseTweak.Applied level = frame.seatedLevel(sub, train, true);
        PoseTweak.Applied seated = frame.leaned(sub, level, roll.suspension);
        WheelSupports.Prepared prepared = WheelSupports.prepare(sub, car.chain(), frame, seated);
        if (prepared == null) {
            return false;
        }

        Pose3d pose = sub.logicalPose();
        double phi = CarriageRoll.twist(seated.orientation(), pose.orientation(), frame.forward0);
        int sideBefore = roll.side;
        int side = CarriageRoll.updateSide(roll, phi);
        if (side != sideBefore) {
            LoconauticsConstants.LOGGER.info("[wheellift] car={} hinge side {} -> {} phi={} deg", shortId(sub),
                    sideBefore, side, f(Math.toDegrees(phi)));
        }
        int pivot = side != 0 && Math.abs(phi) > CarriageRoll.PHI_EPS ? side : 0;
        Vector3d pivotLocal = frame.pointLocal(pivot);
        Vector3d pivotSeated = RollFrame.seatedPoint(sub, seated, pivotLocal);
        Vector3d pivotNow = pose.transformPosition(new Vector3d(pivotLocal), new Vector3d());
        double rise = Math.max(0.0, new Vector3d(pivotNow).sub(pivotSeated).dot(frame.up));

        Vector3d massCenter = pose.transformPosition(new Vector3d(md.getCenterOfMass()), new Vector3d());
        Vector3d vel = handle.getLinearVelocity(new Vector3d());
        Vector3d omega = handle.getAngularVelocity(new Vector3d());
        Vector3d railVel = SableTrainDriver.railVelocityUnder(sub, car);
        double capMps = TrainPropulsion.bodySpeedCapMps(train);
        double vTan = Math.max(-capMps, Math.min(capMps, new Vector3d(vel).sub(railVel).dot(frame.tangent)));
        double omegaR = omega.dot(frame.tangent);
        Vector3d pivotVelocity = new Vector3d(omega).cross(new Vector3d(pivotNow).sub(massCenter)).add(vel)
                .sub(railVel);
        double riseRate = pivotVelocity.dot(frame.up);

        // a real lift is slow, a jump in one solver step is a push out from a contact or a stop
        double kick = 0.0;
        if (roll.preValid) {
            roll.preValid = false;
            kick = Math.max(0.0, vel.dot(frame.up) - roll.preUp - KICK_ACCEL * dt);
        }
        if (kick > KICK_LOG) {
            LoconauticsConstants.LOGGER.info("[wheellift] car={} solver kick {} b/s up removed, rise {} -> {} phi={} deg",
                    shortId(sub), f(kick), f(rise), f(Math.max(0.0, rise - kick * dt)), f(Math.toDegrees(phi)));
        }
        double written = phi;
        double riseWritten = Math.max(0.0, rise - kick * dt);
        double omegaWritten = omegaR;
        if (roll.curveHeld(now)) {
            double limit = Math.max(roll.curveBase,
                    Math.toRadians(LoconauticsConfig.DERAIL_CURVE_LIFT_MAX_DEGREES.get()) + CURVE_CLAMP_MARGIN);
            if (Math.abs(phi) > limit) {
                written = Math.copySign(limit, phi);
                omegaWritten = omegaR * phi > 0.0 ? 0.0 : omegaR;
                if (!roll.curveClamped) {
                    roll.curveClamped = true;
                    LoconauticsConstants.LOGGER.info("[wheellift] car={} curve clamp phi {} -> {} deg curvature={} speed={}",
                            shortId(sub), f(Math.toDegrees(phi)), f(Math.toDegrees(written)), f(roll.curvature),
                            f(new Vector3d(vel).sub(railVel).dot(frame.tangent)));
                }
            }
        }
        double riseRateLeft = riseRate - kick;
        double riseRateWritten = riseWritten <= RISE_EPS && riseRateLeft < 0.0 ? 0.0 : riseRateLeft;

        noteRoll(sub, md, frame, phi, written, omegaWritten, riseWritten, vTan, settling, train);
        CarriageRoll.stepSuspension(roll, frame.half, dt);
        SuspensionLean.publish(sub, roll, frame.forward0, now);
        PoseTweak.Applied leanedSeat = frame.leaned(sub, level, roll.suspension);
        Vector3d pivotWrite = RollFrame.seatedPoint(sub, leanedSeat, pivotLocal);

        Quaterniond rolled = new Quaterniond(leanedSeat.orientation())
                .mul(new Quaterniond().rotateAxis(written, frame.forward0.x, frame.forward0.y, frame.forward0.z))
                .normalize();
        pose.orientation().set(rolled);
        Vector3d pivotTurned = pose.transformPosition(new Vector3d(pivotLocal), new Vector3d());
        Vector3d newPos = new Vector3d(pose.position()).add(pivotWrite).fma(riseWritten, frame.up).sub(pivotTurned);
        Vector3d poseBeforeSnap = new Vector3d(pose.position());
        pose.position().set(newPos);

        double[] heights = wheelHeights(prepared, pose);
        double sink = Math.max(0.0, -java.util.Arrays.stream(heights).min().orElse(0.0));
        com.lycoris.loconautics.trainphysics.debug.RidingBodyTrace.noteSink(id, sink);
        if (sink > 0.0) {
            newPos.fma(sink, frame.up);
            pose.position().set(newPos);
            if (sink > SINK_LOG) {
                LoconauticsConstants.LOGGER.info("[wheellift] car={} sank {} blocks, wheel heights {}", shortId(sub),
                        f(sink), java.util.Arrays.toString(heights));
            }
        }
        com.lycoris.loconautics.common.strand.RopePullTrace.noteSnap(sub, newPos.distance(poseBeforeSnap));
        pipeline.teleport(sub, newPos, rolled);
        com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.holdPivots(sub, train);

        Vector3d pivotFinal = pose.transformPosition(new Vector3d(pivotLocal), new Vector3d());
        Vector3d centerFinal = pose.transformPosition(new Vector3d(md.getCenterOfMass()), new Vector3d());
        Vector3d spin = new Vector3d(frame.tangent).mul(omegaWritten);
        Vector3d pivotTarget = new Vector3d(frame.tangent).mul(vTan).add(railVel).fma(riseRateWritten, frame.up);
        Vector3d velocityTarget = new Vector3d(spin).cross(new Vector3d(centerFinal).sub(pivotFinal))
                .add(pivotTarget);
        double falling = fallingIntoRail(sub, prepared, pose, centerFinal, velocityTarget, spin);
        if (falling < 0.0) {
            velocityTarget.fma(-falling, frame.up);
        }
        Vector3d deltaLinear = velocityTarget.sub(handle.getLinearVelocity(new Vector3d()));
        Vector3d deltaAngular = new Vector3d(spin).sub(handle.getAngularVelocity(new Vector3d()));
        handle.addLinearAndAngularVelocity(deltaLinear, deltaAngular);
        ForceTrace.deltaV(sub, ForceSource.RAIL_SNAP, deltaLinear, ref[0],
                "rolling snap, vTan=" + f(vTan) + " roll=" + f(Math.toDegrees(written)) + " deg rise="
                        + f(riseWritten) + (settling ? " settling" : ""));
        if (CarriageRoll.traceDue(id, now)) {
            traceRoll(sub, train, roll, md, prepared);
        }
        com.lycoris.loconautics.trainphysics.debug.RidingBodyTrace.trace(sub, RailGlue.glueModeOf(sub),
                Math.toDegrees(roll.suspension), now);
        return true;
    }

    private static double[] wheelHeights(WheelSupports.Prepared prepared, Pose3d pose) {
        WheelSupports.Wheel[] wheels = prepared.wheels();
        double[] heights = new double[wheels.length];
        for (int i = 0; i < wheels.length; i++) {
            Vector3d point = pose.transformPosition(new Vector3d(wheels[i].local()), new Vector3d());
            heights[i] = new Vector3d(point).sub(wheels[i].seatPoint()).dot(wheels[i].normal());
        }
        return heights;
    }

    // the most negative speed into the rail of a wheel that rests on it, zero when none is falling
    private static double fallingIntoRail(ServerSubLevel sub, WheelSupports.Prepared prepared, Pose3d pose,
                                          Vector3d center, Vector3d linear, Vector3d spin) {
        double worst = 0.0;
        double[] heights = wheelHeights(prepared, pose);
        WheelSupports.Wheel[] wheels = prepared.wheels();
        for (int i = 0; i < wheels.length; i++) {
            if (heights[i] > CarriageRoll.LIFT_EPS) {
                continue;
            }
            Vector3d point = pose.transformPosition(new Vector3d(wheels[i].local()), new Vector3d());
            Vector3d velocity = new Vector3d(spin).cross(new Vector3d(point).sub(center)).add(linear).sub(
                    TrackHostMotion.railVelocity(sub.getLevel(), wheels[i].host(), wheels[i].rail(), new Vector3d()));
            worst = Math.min(worst, velocity.dot(wheels[i].normal()));
        }
        return worst;
    }

    private static void noteRoll(ServerSubLevel sub, MassData md, RollFrame frame, double phi, double written,
                                 double rollRate, double rise, double vTan, boolean settling, SableTrain train) {
        CarriageRoll.Roll roll = CarriageRoll.of(sub.getUniqueId());
        Vector3d relative = new Vector3d(md.getCenterOfMass()).sub(frame.gLocal);
        double offset = relative.dot(frame.lateral0);
        double height = relative.dot(frame.up0);
        int lean = roll.side != 0 ? roll.side : phi < 0.0 ? -1 : 1;
        Vector3d gravity = DimensionPhysicsData.getGravity(sub.getLevel());
        double cant = gravity.lengthSquared() < 1.0e-9 ? 0.0
                : Math.asin(Math.max(-1.0, Math.min(1.0, lean * frame.lateral.dot(new Vector3d(gravity).normalize()))));
        double tip = CarriageRoll.tippingAngle(frame.half, offset, height, lean, cant);
        boolean curveGrace = roll.curveHeld(sub.getLevel().getGameTime());
        double unglue = curveGrace ? CarriageRoll.unglueAngle(0.0) : CarriageRoll.unglueAngle(vTan);
        roll.phi = written;
        roll.omegaR = rollRate;
        roll.rise = rise;
        roll.lean = lean;
        roll.offset = offset;
        roll.height = height;
        roll.tip = tip;
        roll.unglue = unglue;
        roll.predicted = Math.min(Math.PI / 2.0, CarriageRoll.predictedLift(new Vector3d(gravity), frame.up,
                frame.lateral, frame.half, offset, height));
        roll.speed = vTan;
        long now = sub.getLevel().getGameTime();
        // unglue uses the measured phi on purpose, the log shows the damped one
        if (settling || train.isDerailed()) {
            roll.rolledOff.reset();
            roll.liftedOff.reset();
            return;
        }
        boolean rolledHeld = roll.rolledOff.update(Math.abs(phi) > unglue, now);
        boolean liftedHeld = roll.liftedOff.update(roll.allBeyondFlange(), now);
        if (rolledHeld) {
            LoconauticsConstants.LOGGER.info(
                    "[wheellift] car={} unglue rolled_off phi={} deg limit={} deg tip={} deg speed={} curvature={} grace={} relief={}",
                    shortId(sub), f(Math.toDegrees(phi)), f(Math.toDegrees(unglue)), f(Math.toDegrees(tip)), f(vTan),
                    f(roll.curvature), curveGrace, f(roll.relief));
            train.setDerailed(true, "rolled_off");
            ExternalForceDerail.holdAfterRoll(train);
            RerailBalance.noteRolledOff(sub, train);
        } else if (liftedHeld) {
            LoconauticsConstants.LOGGER.info("[wheellift] car={} unglue lifted_off wheels={} rise={} curvature={}",
                    shortId(sub), java.util.Arrays.toString(roll.y), f(rise), f(roll.curvature));
            train.setDerailed(true, "lifted_off");
            ExternalForceDerail.holdAfterRoll(train);
            RerailBalance.noteRolledOff(sub, train);
        }
    }

    private static void traceRoll(ServerSubLevel sub, SableTrain train, CarriageRoll.Roll roll, MassData md,
                                  WheelSupports.Prepared prepared) {
        StringBuilder wheels = new StringBuilder();
        for (int i = 0; i < roll.y.length; i++) {
            wheels.append(" w").append(i).append("(y=").append(f(roll.y[i])).append(")");
        }
        LoconauticsConstants.LOGGER.info(
                "[wheellift] car={} mode={} lock={} phi={} deg lean={} deg side={} rate={} rise={} e={} h={} tip={} deg unglue={} deg speed={} mass={} lifted={} torque={} coupler={} kappa={} relief={} plates={}{} joints:{}",
                shortId(sub), RailGlue.glueModeOf(sub), roll.lock, f(Math.toDegrees(roll.phi)), f(Math.toDegrees(roll.suspension)), roll.side,
                f(roll.omegaR), f(roll.rise), f(roll.offset), f(roll.height), f(Math.toDegrees(roll.tip)),
                f(Math.toDegrees(roll.unglue)), f(roll.speed), f(md.getMass()), roll.lifted, f(roll.torque),
                f(roll.couplerTorque), f(roll.curvature), f(roll.relief),
                com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.plateRollLine(sub, train), wheels,
                WheelSupports.impulseLine(sub.getUniqueId()));
        WheelSupports.traceColumns(sub, prepared);
    }
}
