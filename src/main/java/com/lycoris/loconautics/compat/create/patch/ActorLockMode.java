package com.lycoris.loconautics.compat.create.patch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public enum ActorLockMode {
    ALWAYS_UNLOCKED,
    ALWAYS_LOCKED,
    LOCKED_WHEN_POWERED,
    LOCKED_WHEN_UNPOWERED;

    public boolean isLocked(Level level, BlockPos pos) {
        return switch (this) {
            case ALWAYS_UNLOCKED -> false;
            case ALWAYS_LOCKED -> true;
            case LOCKED_WHEN_POWERED -> level.hasNeighborSignal(pos);
            case LOCKED_WHEN_UNPOWERED -> !level.hasNeighborSignal(pos);
        };
    }
}
