package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class PlateGraceCallback implements BlockSubLevelCollisionCallback {

    private final BlockSubLevelCollisionCallback inner;

    public PlateGraceCallback(@Nullable BlockSubLevelCollisionCallback inner) {
        this.inner = inner;
    }

    @Override
    public CollisionResult sable$onCollision(BlockPos blockPos, @Nullable BlockPos otherBlockPos,
                                             Vector3d hitPos, double impactVelocity) {
        if (otherBlockPos != null && TurntableRegistry.anyPlate() && graceApplies(blockPos, otherBlockPos)) {
            return new CollisionResult(new Vector3d(), true);
        }
        return inner == null ? CollisionResult.NONE
                : inner.sable$onCollision(blockPos, otherBlockPos, hitPos, impactVelocity);
    }

    private static boolean graceApplies(BlockPos blockPos, BlockPos otherBlockPos) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        ServerLevel level = system == null ? null : system.getLevel();
        if (level == null || !(SableCompanion.INSTANCE.getContaining(level, blockPos.getX() >> 4,
                blockPos.getZ() >> 4) instanceof ServerSubLevel plate) || !TurntableRegistry.isPlate(plate.getUniqueId())) {
            return false;
        }
        if (SableCompanion.INSTANCE.getContaining(level, otherBlockPos.getX() >> 4,
                otherBlockPos.getZ() >> 4) != null) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(blockPos.relative(side)).isAir()) {
                if (TurntableTrace.due("grace" + plate.getUniqueId(), level.getGameTime(), 40)) {
                    TurntableTrace.log("grace plate " + plate.getUniqueId().toString().substring(0, 8) + " edge "
                            + blockPos.toShortString() + " world " + otherBlockPos.toShortString());
                }
                return true;
            }
        }
        return false;
    }
}
