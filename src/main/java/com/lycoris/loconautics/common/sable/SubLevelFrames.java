package com.lycoris.loconautics.common.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SubLevelFrames {

    private SubLevelFrames() {
    }

    public static Vec3 lookToLocal(Level level, BlockPos localPos, Vec3 worldLook) {
        SubLevel host = Sable.HELPER.getContaining(level, localPos);
        return host == null ? worldLook : host.logicalPose().transformNormalInverse(worldLook);
    }

    public static Vec3 lookToLocal(BlockGetter getter, BlockPos localPos, Vec3 worldLook) {
        if (!(getter instanceof Level level)) {
            return worldLook;
        }
        Vec3 local = lookToLocal(level, localPos, worldLook);
        return local;
    }

    public static boolean closerInWorld(Level level, Vec3i a, Vec3i b, double distance) {
        Vec3 worldA = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(a));
        Vec3 worldB = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(b));
        return worldA.distanceToSqr(worldB) < distance * distance;
    }
}
