package com.lycoris.loconautics.common.grab;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public interface GrabbableActor extends GrabTarget {

    BlockPos getBlockPos();

    @Override
    default Vector3d getGrabCenter() {
        BlockPos pos = getBlockPos();
        return new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    default Vec3 worldCenter(SubLevel subLevel) {
        Vector3d w = subLevel.logicalPose().transformPosition(getGrabCenter(), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }
}
