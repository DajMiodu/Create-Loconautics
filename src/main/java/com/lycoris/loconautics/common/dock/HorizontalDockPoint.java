package com.lycoris.loconautics.common.dock;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class HorizontalDockPoint {

    private HorizontalDockPoint() {
    }

    public static Vec3 anchorLocal(BlockEntity be, Direction face, double seatOffset) {
        return Vec3.atCenterOf(be.getBlockPos())
                .add(Vec3.atLowerCornerOf(face.getNormal()).scale(seatOffset));
    }

    public static Vec3 worldPoint(BlockEntity be, Vec3 anchorLocal) {
        SubLevel sub = Sable.HELPER.getContaining(be);
        if (sub == null) {
            return anchorLocal;
        }
        Vector3d w = sub.logicalPose().transformPosition(
                new Vector3d(anchorLocal.x, anchorLocal.y, anchorLocal.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    public static Quaterniond orientation(Direction face) {
        Vec3i out = face.getNormal();
        return new Quaterniond().rotationTo(0.0, 1.0, 0.0, out.getX(), out.getY(), out.getZ());
    }
}
