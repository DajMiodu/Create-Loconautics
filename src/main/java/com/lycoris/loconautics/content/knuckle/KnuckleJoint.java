package com.lycoris.loconautics.content.knuckle;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KnuckleJoint {

    private KnuckleJoint() {
    }

    public static final class Live {
        private final GenericConstraintHandle handle;
        private final UUID subA;
        private final UUID subB;

        private Live(GenericConstraintHandle handle, UUID subA, UUID subB) {
            this.handle = handle;
            this.subA = subA;
            this.subB = subB;
        }
    }

    private static final double WAKE_ERROR = 0.002;

    private static final Set<Live> ALL = ConcurrentHashMap.newKeySet();

    public static Live hold(Live live, ServerSubLevel subA, Vector3dc hingeA, ServerSubLevel subB, Vector3dc hingeB,
                            double startGap, double gain) {
        if (subA.isRemoved() || subB.isRemoved()) {
            release(live);
            return null;
        }
        Vector3d worldA = subA.logicalPose().transformPosition(new Vector3d(hingeA));
        Vector3d worldB = subB.logicalPose().transformPosition(new Vector3d(hingeB));
        Vector3d line = worldB.sub(worldA, new Vector3d());
        double gap = line.length();
        if (gap < 1.0e-6) {
            return live;
        }
        line.div(gap);

        ServerSubLevelContainer container = SubLevelContainer.getContainer(subA.getLevel());
        MassData massA = subA.getMassTracker();
        MassData massB = subB.getMassTracker();
        if (container == null || massA.isInvalid() || massB.isInvalid()) {
            return live;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        Live current = live != null && live.handle.isValid() ? live : create(pipeline, subA, hingeA, subB, hingeB);
        if (current == null) {
            return null;
        }

        Quaterniond frame = aimedFrame(line);
        Quaterniond localA = new Quaterniond(subA.logicalPose().orientation()).conjugate().mul(frame);
        Quaterniond localB = new Quaterniond(subB.logicalPose().orientation()).conjugate().mul(frame);
        current.handle.setFrame1(hingeA, localA);

        double length = startGap + (KnuckleTuning.REST_LENGTH - startGap) * gain;
        current.handle.setFrame2(hingeB, localB);
        double accel = KnuckleTuning.MAX_ACCEL;
        // a joint never wakes a sleeping body, so a parked car would sit where it is while the pair is out of place
        if (Math.abs(gap - length) > WAKE_ERROR) {
            pipeline.wakeUp(subA);
            pipeline.wakeUp(subB);
        }
        // the force is capped by the pairs own mass, so a stuck or derailed car is dragged, never thrown
        double effectiveMass = massA.getMass() * massB.getMass() / (massA.getMass() + massB.getMass());
        current.handle.setMotor(ConstraintJointAxis.LINEAR_X, length, KnuckleTuning.springStiffness,
                2.0 * Math.sqrt(KnuckleTuning.springStiffness), true, effectiveMass * accel);
        return current;
    }

    private static Live create(PhysicsPipeline pipeline, ServerSubLevel subA, Vector3dc hingeA, ServerSubLevel subB, Vector3dc hingeB) {
        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
                new Vector3d(hingeA), new Vector3d(hingeB), new Quaterniond(), new Quaterniond());
        GenericConstraintHandle handle = pipeline.addConstraint(subA, subB, config);
        if (handle == null) {
            return null;
        }
        handle.setContactsEnabled(false);
        Live live = new Live(handle, subA.getUniqueId(), subB.getUniqueId());
        ALL.add(live);
        return live;
    }

    private static Quaterniond aimedFrame(Vector3d line) {
        Vector3d reference = Math.abs(line.y) > 0.99 ? new Vector3d(1.0, 0.0, 0.0) : new Vector3d(0.0, 1.0, 0.0);
        Vector3d lateral = line.cross(reference, new Vector3d()).normalize();
        Vector3d up = lateral.cross(line, new Vector3d());
        Matrix3d basis = new Matrix3d();
        basis.setColumn(0, line);
        basis.setColumn(1, up);
        basis.setColumn(2, lateral);
        return basis.getNormalizedRotation(new Quaterniond());
    }

    public static void release(Live live) {
        if (live == null) {
            return;
        }
        ALL.remove(live);
        if (live.handle.isValid()) {
            live.handle.remove();
        }
    }

    // joint first, body second, or rapier throws a fit
    public static void releaseAllFor(UUID body) {
        for (Live live : ALL) {
            if (live.subA.equals(body) || live.subB.equals(body)) {
                release(live);
            }
        }
    }

    public static void clear() {
        KnuckleRailCoupler.clear();
        ALL.clear();
    }
}
