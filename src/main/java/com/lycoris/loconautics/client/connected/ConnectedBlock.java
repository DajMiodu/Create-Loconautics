package com.lycoris.loconautics.client.connected;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public interface ConnectedBlock {

    default boolean connectsTo(BlockGetter level, BlockState state, BlockPos anchor, BlockPos neighborPos) {
        return level.getBlockState(neighborPos).is(state.getBlock());
    }

    default ConnectionBounds connectionBounds() {
        return ConnectionBounds.UNBOUNDED;
    }

    default BlockPos groupAnchor(BlockGetter level, BlockState state, BlockPos pos) {
        return pos;
    }

    default boolean isOpenFace(BlockState state, Direction face) {
        return false;
    }
}
