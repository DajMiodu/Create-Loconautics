package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.BodyRef;
import com.lycoris.loconautics.foundation.debug.DebugLines;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.f;
import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class WheelSupports {

    private WheelSupports() {
    }

    // side is 1 on the left of the chord and 1 on the right, host is null on a rail in the world
    public record Wheel(Vector3d rail, Vector3d seatPoint, Vector3d local, Vector3d normal, Vector3d tangent,
                        int side, ServerSubLevel host) {
    }

    public record Prepared(Wheel[] wheels, RailCarriage.PointFrame first, RailCarriage.PointFrame last) {
    }

    private record Support(GenericConstraintHandle handle, UUID hostId) {
    }

    private static final ConcurrentHashMap<UUID, Support[]> SUPPORTS = new ConcurrentHashMap<>();

    public static Prepared prepare(ServerSubLevel sub, List<SableTrain.Bogey> chain, RollFrame frame,
                                   PoseTweak.Applied seated) {
        Wheel[] wheels = new Wheel[chain.size() * 2];
        Quaterniond seatInverse = new Quaterniond(frame.seat).conjugate();
        RailCarriage.PointFrame first = null;
        RailCarriage.PointFrame last = null;
        for (int i = 0; i < chain.size(); i++) {
            RailCarriage rail = chain.get(i).rail();
            RailCarriage.PointFrame point = rail.railFrame(frame.tangent);
            if (point == null) {
                return null;
            }
            if (i == 0) {
                first = point;
            }
            last = point;
            Vector3d lateral = new Vector3d(point.t()).cross(point.n()).normalize();
            double half = RailGauge.halfGauge(rail.trackMaterial());
            ServerSubLevel host = rail.activeSub();
            for (int index = 0; index < 2; index++) {
                int side = index == 0 ? -1 : 1;
                Vector3d onRail = new Vector3d(point.c()).fma(CarriageRoll.RAIL_TOP, point.n())
                        .fma(side * half, lateral);
                Vector3d local = seatInverse.transform(new Vector3d(onRail).sub(frame.reference))
                        .add(frame.anchorLocal);
                wheels[i * 2 + index] = new Wheel(onRail, RollFrame.seatedPoint(sub, seated, local), local,
                        new Vector3d(point.n()), new Vector3d(point.t()), side, host);
            }
        }
        return new Prepared(wheels, first, last);
    }

    public static void drive(PhysicsPipeline pipeline, ServerSubLevel sub, Car car, SableTrain train,
                             RollFrame frame, Prepared prepared, PoseTweak.Applied seated, double dt) {
        UUID id = sub.getUniqueId();
        MassData md = sub.getMassTracker();
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        Wheel[] wheels = prepared.wheels();
        Pose3d pose = sub.logicalPose();
        CarriageRoll.Roll roll = CarriageRoll.of(id);
        long now = sub.getLevel().getGameTime();
        CarriageRoll.beginStep(roll, now);

        double phi = CarriageRoll.twist(seated.orientation(), pose.orientation(), frame.forward0);
        int side = CarriageRoll.updateSide(roll, phi);

        int count = wheels.length;
        double[] y = new double[count];
        int lifted = 0;
        for (int i = 0; i < count; i++) {
            Wheel wheel = wheels[i];
            Vector3d point = pose.transformPosition(new Vector3d(wheel.local()), new Vector3d());
            y[i] = new Vector3d(point).sub(wheel.seatPoint()).dot(wheel.normal());
            if (y[i] > CarriageRoll.LIFT_EPS) {
                lifted++;
            }
        }
        roll.y = y;
        roll.lifted = lifted;

        Vector3d massCenter = pose.transformPosition(new Vector3d(md.getCenterOfMass()), new Vector3d());
        noteHinge(sub, roll, md, pose, frame, seated, side, massCenter);
        noteMassChange(sub, roll, md, phi, y);
        roll.preUp = handle.getLinearVelocity(new Vector3d()).dot(frame.up);
        roll.preValid = true;

        ForceTotal forces = new ForceTotal();
        RollDynamics.Step step = new RollDynamics.Step(sub, car, md, handle, pose, frame, prepared, massCenter,
                dt, forces);
        boolean pushed = RollDynamics.torsion(step, phi, side);
        pushed |= RollDynamics.curveRelief(step, phi, side);
        if (pushed) {
            pipeline.wakeUp(sub);
            handle.applyForcesAndReset(forces);
        }
        updateJoints(pipeline, sub, wheels);
    }

    private static void noteHinge(ServerSubLevel sub, CarriageRoll.Roll roll, MassData md, Pose3d pose,
                                  RollFrame frame, PoseTweak.Applied seated, int side, Vector3d massCenter) {
        double gN = Math.max(0.0, -DimensionPhysicsData.getGravity(sub.getLevel()).dot(frame.up));
        roll.axis = new Vector3d(frame.tangent);
        roll.up = new Vector3d(frame.up);
        roll.seat = new Quaterniond(seated.orientation());
        roll.half = frame.half;
        roll.mass = md.getMass();
        roll.hingeK = RollDynamics.hingeStiffness(md.getMass(), gN, frame.half);
        roll.hingeI = RollDynamics.hingeInertia(md, pose, frame, side, massCenter);
        roll.couplerTorque = 0.0;
        roll.couplerTwist = 0.0;
    }

    private static void noteMassChange(ServerSubLevel sub, CarriageRoll.Roll roll, MassData md, double phi,
                                       double[] y) {
        double mass = md.getMass();
        Vector3d center = new Vector3d(md.getCenterOfMass());
        if (roll.lastMass >= 0.0
                && (Math.abs(mass - roll.lastMass) > 0.05 * roll.lastMass || center.distance(roll.lastCenter) > 0.1)) {
            LoconauticsConstants.LOGGER.info(
                    "[wheellift] car={} mass event mass {} -> {} com {} -> {} phi={} deg rise={} y={}", shortId(sub),
                    f(roll.lastMass), f(mass), roll.lastCenter, center, f(Math.toDegrees(phi)), f(roll.rise),
                    java.util.Arrays.toString(y));
        }
        roll.lastMass = mass;
        roll.lastCenter.set(center);
    }

    private static void updateJoints(PhysicsPipeline pipeline, ServerSubLevel sub, Wheel[] wheels) {
        UUID id = sub.getUniqueId();
        Support[] supports = SUPPORTS.get(id);
        if (supports == null || supports.length != wheels.length) {
            release(id);
            supports = new Support[wheels.length];
            SUPPORTS.put(id, supports);
        }
        for (int i = 0; i < wheels.length; i++) {
            Wheel wheel = wheels[i];
            Support held = supports[i];
            if (BodyRef.ofBody(wheel.host()).absent()) {
                dropSupport(supports, i);
                continue;
            }
            UUID hostId = wheel.host() == null ? null : wheel.host().getUniqueId();
            Vector3d pos1 = wheel.host() == null ? new Vector3d(wheel.seatPoint())
                    : wheel.host().logicalPose().transformPositionInverse(new Vector3d(wheel.seatPoint()),
                    new Vector3d());
            Quaterniond frame1 = hostFrame(wheel);
            if (held != null && held.handle().isValid() && Objects.equals(held.hostId(), hostId)) {
                held.handle().setFrame1(pos1, frame1);
                held.handle().setFrame2(new Vector3d(wheel.local()), new Quaterniond());
                continue;
            }
            dropSupport(supports, i);
            PhysicsPipelineBody bodyA = wheel.host();
            GenericConstraintConfiguration config = new GenericConstraintConfiguration(pos1,
                    new Vector3d(wheel.local()), frame1, new Quaterniond(), EnumSet.noneOf(ConstraintJointAxis.class));
            GenericConstraintHandle created = pipeline.addConstraint(bodyA, sub, config);
            if (created == null) {
                continue;
            }
            created.setLimit(ConstraintJointAxis.LINEAR_Y, 0.0, CarriageRoll.LIFT_RANGE);
            supports[i] = new Support(created, hostId);
            LoconauticsConstants.LOGGER.info("[wheellift] car={} support {} created on host {}", shortId(id), i,
                    shortId(wheel.host()));
        }
    }

    private static Quaterniond hostFrame(Wheel wheel) {
        Vector3d joint = new Vector3d(wheel.tangent()).cross(wheel.normal()).normalize();
        Quaterniond world = RailGlue.basisQuaternion(wheel.tangent(), wheel.normal(), joint);
        if (wheel.host() == null) {
            return world;
        }
        return new Quaterniond(wheel.host().logicalPose().orientation()).conjugate().mul(world);
    }

    private static void dropSupport(Support[] supports, int index) {
        Support held = supports[index];
        if (held != null && held.handle().isValid()) {
            held.handle().remove();
        }
        supports[index] = null;
    }

    public static void release(UUID id) {
        Support[] supports = SUPPORTS.remove(id);
        if (supports == null) {
            return;
        }
        for (Support held : supports) {
            if (held != null && held.handle().isValid()) {
                held.handle().remove();
            }
        }
        LoconauticsConstants.LOGGER.info("[wheellift] car={} supports released", shortId(id));
    }

    public static String impulseLine(UUID id) {
        Support[] supports = SUPPORTS.get(id);
        if (supports == null) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < supports.length; i++) {
            Support held = supports[i];
            if (held == null || !held.handle().isValid()) {
                out.append(" [").append(i).append(" none]");
                continue;
            }
            Vector3d linear = new Vector3d();
            held.handle().getJointImpulses(linear, new Vector3d());
            out.append(" [").append(i).append(" jointY=").append(f(linear.y)).append("]");
        }
        return out.toString();
    }

    public static void traceColumns(ServerSubLevel sub, Prepared prepared) {
        Wheel[] wheels = prepared.wheels();
        CarriageRoll.Roll roll = CarriageRoll.of(sub.getUniqueId());
        if (roll.y.length != wheels.length) {
            return;
        }
        String car8 = shortId(sub.getUniqueId());
        for (int i = 0; i < wheels.length; i++) {
            Vec3 at = new Vec3(wheels[i].rail().x, wheels[i].rail().y, wheels[i].rail().z);
            int color = roll.y[i] > CarriageRoll.LIFT_EPS ? 0xFF3333 : 0x33FF33;
            DebugLines.column(sub.getLevel(), "wl_" + car8 + "_" + i, at, color);
        }
        for (int index = 0; index < 2; index++) {
            Wheel first = wheels[index];
            Wheel last = wheels[wheels.length - 2 + index];
            DebugLines.line(sub.getLevel(), "wl_" + car8 + "_hinge" + index,
                    new Vec3(first.rail().x, first.rail().y, first.rail().z),
                    new Vec3(last.rail().x, last.rail().y, last.rail().z), 0xFFFF00);
        }
    }

    public static void retain(Set<UUID> live) {
        for (UUID id : List.copyOf(SUPPORTS.keySet())) {
            if (!live.contains(id)) {
                release(id);
            }
        }
    }

    public static void clearAll() {
        SUPPORTS.clear();
    }
}
