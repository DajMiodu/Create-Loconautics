package com.lycoris.loconautics.common.sable;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

public final class SubLevelData {

    private SubLevelData() {
    }

    public static Pose3dc pose(SubLevel sub) {
        return sub.logicalPose();
    }

    public static ServerSubLevel resolve(ServerLevel level, UUID id) {
        if (level == null || id == null) {
            return null;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !(container.getSubLevel(id) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        return sub;
    }

    public static Vector3d position(SubLevel sub) {
        return new Vector3d(sub.logicalPose().position());
    }

    public static double altitude(SubLevel sub) {
        return sub.logicalPose().position().y();
    }

    public static Vector3d worldOf(SubLevel sub, Vector3dc local) {
        return sub.logicalPose().transformPosition(new Vector3d(local), new Vector3d());
    }

    public static Vector3d worldOfBlock(SubLevel sub, BlockPos local) {
        return worldOf(sub, new Vector3d(local.getX() + 0.5, local.getY() + 0.5, local.getZ() + 0.5));
    }

    public static Vector3d worldOfOffset(SubLevel sub, Vec3i offset) {
        return worldOfBlock(sub, sub.getPlot().getCenterBlock().offset(offset));
    }

    public static Vector3d localOf(SubLevel sub, Vector3dc world) {
        return sub.logicalPose().transformPositionInverse(new Vector3d(world), new Vector3d());
    }

    public static double altitudeOfOffset(SubLevel sub, Vec3i offset) {
        return worldOfOffset(sub, offset).y;
    }

    public static Quaterniondc orientation(SubLevel sub) {
        return sub.logicalPose().orientation();
    }

    public static Vector3d directionOf(SubLevel sub, Vector3dc local) {
        return sub.logicalPose().transformNormal(new Vector3d(local), new Vector3d());
    }

    public static Vector3d forward(SubLevel sub) {
        return directionOf(sub, new Vector3d(0.0, 0.0, 1.0));
    }

    public static Vector3d up(SubLevel sub) {
        return directionOf(sub, new Vector3d(0.0, 1.0, 0.0));
    }

    public static Vector3d right(SubLevel sub) {
        return directionOf(sub, new Vector3d(1.0, 0.0, 0.0));
    }

    public static double yawDeg(SubLevel sub) {
        return yawDeg(orientation(sub), new Vector3d(0.0, 0.0, 1.0));
    }

    public static double yawDeg(Quaterniondc orientation, Vector3dc localForward) {
        Vector3d f = orientation.transform(new Vector3d(localForward));
        return Math.toDegrees(Math.atan2(-f.x, f.z));
    }

    public static double pitchDeg(Quaterniondc orientation, Vector3dc localForward) {
        Vector3d f = orientation.transform(new Vector3d(localForward));
        return Math.toDegrees(Math.asin(-Math.max(-1.0, Math.min(1.0, f.y))));
    }

    public static double rollDeg(Quaterniondc orientation, Vector3dc localForward) {
        Vector3d up = orientation.transform(new Vector3d(0.0, 1.0, 0.0));
        Vector3d right = orientation.transform(new Vector3d(localForward.z(), 0.0, -localForward.x()));
        return Math.toDegrees(Math.atan2(right.y, up.y));
    }

    public static boolean isUpright(SubLevel sub, double minUpDot) {
        return up(sub).y >= minUpDot;
    }


    private static RigidBodyHandle body(SubLevel sub) {
        if (!(sub instanceof ServerSubLevel server)) {
            return null;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(server);
        return handle == null || !handle.isValid() ? null : handle;
    }

    public static Vector3d linearVelocity(SubLevel sub) {
        RigidBodyHandle handle = body(sub);
        return handle == null ? null : handle.getLinearVelocity(new Vector3d());
    }

    public static Vector3d angularVelocity(SubLevel sub) {
        RigidBodyHandle handle = body(sub);
        return handle == null ? null : handle.getAngularVelocity(new Vector3d());
    }

    public static void stopLinear(SubLevel sub) {
        RigidBodyHandle handle = body(sub);
        if (handle == null) {
            return;
        }
        handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(), new Vector3d());
    }

    public static boolean atRest(SubLevel sub, double maxSpeed) {
        Vector3d lin = linearVelocity(sub);
        Vector3d ang = angularVelocity(sub);
        if (lin == null || ang == null) {
            return true;
        }
        return lin.length() <= maxSpeed && ang.length() <= maxSpeed;
    }

    public static double headingDiffDeg(Vector3dc a, Vector3dc b) {
        double la = Math.hypot(a.x(), a.z());
        double lb = Math.hypot(b.x(), b.z());
        if (la < 1.0e-9 || lb < 1.0e-9) {
            return 90.0;
        }
        double dot = (a.x() * b.x() + a.z() * b.z()) / (la * lb);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    public static double axisDiffDeg(Vector3dc a, Vector3dc b) {
        double diff = headingDiffDeg(a, b);
        return diff > 90.0 ? 180.0 - diff : diff;
    }

    public static Vector3d of(Vec3 v) {
        return new Vector3d(v.x, v.y, v.z);
    }

    public static Vec3 toVec3(Vector3dc v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
