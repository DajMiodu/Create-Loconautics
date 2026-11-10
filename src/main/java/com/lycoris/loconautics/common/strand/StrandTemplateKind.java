package com.lycoris.loconautics.common.strand;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface StrandTemplateKind {

    @Nullable
    CompoundTag snapshot(ServerLevel level, BlockEntity self, UUID strandId);

    boolean restore(ServerLevel level, BlockEntity self, BlockEntity far, CompoundTag record);

    @Nullable
    StrandTemplateState stateOf(BlockEntity be);

    default boolean farMayLackRecord() {
        return false;
    }
}
