package com.lycoris.loconautics.trainphysics.sublevel;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class SablePlotFrame {

    private SablePlotFrame() {
    }

    public static Vec3 toLocalPos(ServerSubLevel sub, Vec3 world) {
        Vector3d l = sub.logicalPose().transformPositionInverse(
                new Vector3d(world.x, world.y, world.z), new Vector3d());
        return new Vec3(l.x, l.y, l.z);
    }

    public static Vec3 toWorldPos(ServerSubLevel sub, Vec3 local) {
        Vector3d w = sub.logicalPose().transformPosition(
                new Vector3d(local.x, local.y, local.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    public static Vec3 toLocalDir(ServerSubLevel sub, Vec3 world) {
        Vector3d l = sub.logicalPose().transformNormalInverse(
                new Vector3d(world.x, world.y, world.z), new Vector3d());
        return new Vec3(l.x, l.y, l.z);
    }
}
