package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrackHostMotion;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import static com.lycoris.loconautics.foundation.LogFormat.f;

public final class RollDynamics {

    private RollDynamics() {
    }

    public static final double CURVE_CURVATURE = 0.01;
    private static final long CURVE_HOLD_TICKS = 40L;
    private static final double FAST_CURVE_CUT = 0.75;

    public record Step(ServerSubLevel sub, Car car, MassData md, RigidBodyHandle handle, Pose3d pose,
                       RollFrame frame, WheelSupports.Prepared prepared, Vector3d massCenter, double dt,
                       ForceTotal forces) {
    }

    // the unclamped linear hinge stiffness, the torque that holds a centered car at the reference lift
    public static double hingeStiffness(double mass, double gN, double half) {
        double phiRef = Math.toRadians(LoconauticsConfig.DERAIL_WHEEL_LIFT_DEGREES.get());
        return mass * gN * half / phiRef;
    }

    // inertia about the hinge line through the side rail, or the center line for side 0
    public static double hingeInertia(MassData md, Pose3d pose, RollFrame frame, int side, Vector3d massCenter) {
        Vector3d hinge = pose.transformPosition(frame.pointLocal(side), new Vector3d());
        Vector3d arm = new Vector3d(massCenter).sub(hinge);
        Vector3d armPerp = arm.sub(new Vector3d(frame.tangent).mul(arm.dot(frame.tangent)));
        Vector3d tangentLocal = new Quaterniond(pose.orientation()).conjugate().transform(new Vector3d(frame.tangent));
        return tangentLocal.dot(md.getInertiaTensor().transform(tangentLocal, new Vector3d()))
                + md.getMass() * armPerp.lengthSquared();
    }

    // a linear, critically damped torsion about the hinge rail pulls the lifted wheels back down
    static boolean torsion(Step s, double phi, int side) {
        CarriageRoll.Roll roll = CarriageRoll.of(s.sub().getUniqueId());
        roll.torque = 0.0;
        if (side == 0) {
            return false;
        }
        RollFrame frame = s.frame();
        double mass = s.md().getMass();
        double gN = -gravity(s).dot(frame.up);
        if (gN <= 0.0) {
            return false;
        }
        double inertia = hingeInertia(s.md(), s.pose(), frame, side, s.massCenter());
        if (!(inertia > 1.0e-6)) {
            return false;
        }
        double dt = s.dt();
        double stiffness = Math.min(hingeStiffness(mass, gN, frame.half), 0.25 * inertia / (dt * dt));
        double ratio = LoconauticsConfig.DERAIL_WHEEL_LIFT_DAMPING.get();
        double damping = Math.min(2.0 * ratio * Math.sqrt(stiffness * inertia), 0.5 * inertia / dt);
        double rate = s.handle().getAngularVelocity(new Vector3d()).dot(frame.tangent);
        double lift = Math.min(Math.max(0.0, side * phi), CarriageRoll.holdAngle());
        double torque = Math.max(0.0, stiffness * lift + damping * side * rate);
        roll.torque = torque;
        if (torque <= 0.0) {
            return false;
        }
        WheelSupports.Wheel[] wheels = s.prepared().wheels();
        int lifted = 0;
        for (int i = 0; i < wheels.length; i++) {
            if (wheels[i].side() == -side && roll.y[i] > 0.0) {
                lifted++;
            }
        }
        if (lifted == 0) {
            return false;
        }
        double force = torque / (2.0 * frame.half) / lifted;
        for (int i = 0; i < wheels.length; i++) {
            WheelSupports.Wheel wheel = wheels[i];
            if (wheel.side() != -side || roll.y[i] <= 0.0) {
                continue;
            }
            Vector3d worldImpulse = new Vector3d(wheel.normal()).mul(-force * dt);
            s.forces().applyImpulseAtPoint(s.md(), wheel.local(), bodyInverse(s).transform(new Vector3d(worldImpulse)));
            TrackHostMotion.pushHost(wheel.host(), wheel.rail(), worldImpulse);
            ForceTrace.impulse(s.sub(), ForceSource.HINGE, worldImpulse, wheel.rail(),
                    "wheel " + i + " y=" + f(roll.y[i]) + " torque=" + f(torque));
        }
        return true;
    }

    // the window outlives the curve, the roll it built is still swinging at the exit
    private static void noteCurve(CarriageRoll.Roll roll, long now, double curvature, double phi) {
        if (Math.abs(curvature) < CURVE_CURVATURE || LoconauticsConfig.DERAIL_ON_CURVE.get()) {
            return;
        }
        if (!roll.curveHeld(now)) {
            roll.curveBase = Math.abs(phi);
            roll.curveClamped = false;
        }
        roll.curveUntil = now + CURVE_HOLD_TICKS;
    }

    // relief share of the centripetal force acts at the center of mass, the guide carries the rest
    static boolean curveRelief(Step s, double phi, int side) {
        CarriageRoll.Roll roll = CarriageRoll.of(s.sub().getUniqueId());
        roll.relief = 0.0;
        roll.curvature = 0.0;
        RollFrame frame = s.frame();
        WheelSupports.Prepared prepared = s.prepared();
        double span = prepared.first().c().distance(prepared.last().c());
        if (span < 1.0e-3) {
            return false;
        }
        Vector3d bend = new Vector3d(prepared.last().t()).sub(prepared.first().t()).div(span);
        double curvature = bend.dot(frame.lateral);
        roll.curvature = curvature;
        noteCurve(roll, s.sub().getLevel().getGameTime(), curvature, phi);
        Vector3d railVelocity = SableTrainDriver.railVelocityUnder(s.sub(), s.car());
        double speed = s.handle().getLinearVelocity(new Vector3d()).sub(railVelocity).dot(frame.tangent);
        double need = speed * speed * curvature;
        if (Math.abs(need) < 1.0e-6 || LoconauticsConfig.DERAIL_ON_CURVE.get()) {
            return false;
        }
        double mass = s.md().getMass();
        Vector3d gravity = gravity(s);
        double gN = -gravity.dot(frame.up);
        int outside = curvature > 0.0 ? -1 : 1;
        Vector3d relative = new Vector3d(s.md().getCenterOfMass()).sub(frame.gLocal);
        double offset = relative.dot(frame.lateral0);
        double height = relative.dot(frame.up0);
        double gSide = gravity.dot(frame.lateral) * outside;
        double hold = mass * (gN * (frame.half - outside * offset) - gSide * height);
        double phiRef = Math.toRadians(LoconauticsConfig.DERAIL_WHEEL_LIFT_DEGREES.get());
        double phiCap = Math.toRadians(LoconauticsConfig.DERAIL_CURVE_LIFT_MAX_DEGREES.get())
                * (1.0 - FAST_CURVE_CUT * CarriageRoll.speedBlend(speed));
        double allowed = hold + phiCap / phiRef * mass * gN * frame.half;
        Vector3d guide = s.pose().transformPosition(
                side != 0 && Math.abs(phi) > CarriageRoll.PHI_EPS ? frame.pointLocal(side) : frame.gLocal,
                new Vector3d());
        double guideHeight = Math.max(0.0, new Vector3d(s.massCenter()).sub(guide).dot(frame.up));
        double overturn = mass * Math.abs(need) * guideHeight;
        if (overturn < 1.0e-6) {
            return false;
        }
        double relief = 1.0 - Math.max(0.0, Math.min(1.0, allowed / overturn));
        roll.relief = relief;
        if (relief <= 0.0) {
            return false;
        }
        Vector3d worldImpulse = new Vector3d(frame.lateral).mul(relief * mass * need * s.dt());
        s.forces().applyLinearImpulse(bodyInverse(s).transform(new Vector3d(worldImpulse)));
        TrackHostMotion.pushHost(TrainRailAdvance.leadingActiveSub(s.car()), frame.reference, worldImpulse);
        ForceTrace.impulse(s.sub(), ForceSource.CURVE_RELIEF, worldImpulse, s.massCenter(),
                "curve relief " + f(relief) + " need=" + f(need) + " overturn=" + f(overturn) + " allowed="
                        + f(allowed));
        return true;
    }

    private static Quaterniond bodyInverse(Step s) {
        return new Quaterniond(s.pose().orientation()).conjugate();
    }

    private static Vector3d gravity(Step s) {
        return new Vector3d(DimensionPhysicsData.getGravity(s.sub().getLevel()));
    }
}
