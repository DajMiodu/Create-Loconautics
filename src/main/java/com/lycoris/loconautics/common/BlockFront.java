package com.lycoris.loconautics.common;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BlockFront {

    private BlockFront() {}

    public static Vec3 frontPoint(Level level, BlockPos pos, Direction facing) {
        return Sable.HELPER.projectOutOfSubLevel(level,
                Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal())));
    }

    public static Vec3 backPoint(Level level, BlockPos pos, Direction facing) {
        return frontPoint(level, pos, facing.getOpposite());
    }

    public static boolean isInFront(Level level, BlockPos pos, Direction facing, Vec3 point, double radius) {
        double toFront = point.distanceToSqr(frontPoint(level, pos, facing));
        return toFront < point.distanceToSqr(backPoint(level, pos, facing))
                && toFront <= radius * radius;
    }
}
