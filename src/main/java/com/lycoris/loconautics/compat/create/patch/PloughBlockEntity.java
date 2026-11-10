package com.lycoris.loconautics.compat.create.patch;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class PloughBlockEntity extends SmartBlockEntity {

    ActorLockScroll lockScroll;

    public PloughBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        lockScroll = new ActorLockScroll(this);
        behaviours.add(lockScroll);
    }

    public boolean isLocked() {
        if (level == null) return false;
        return lockScroll.getMode().isLocked(level, worldPosition);
    }
}
