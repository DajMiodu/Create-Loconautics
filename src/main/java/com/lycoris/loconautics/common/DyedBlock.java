package com.lycoris.loconautics.common;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface DyedBlock {

    DyeColor getColor();

    BlockState paintedState(BlockState state, DyeColor dyed);

    default void applyPaint(Level level, BlockPos pos, BlockState painted) {
        level.setBlock(pos, painted, 3);
    }

    default ItemInteractionResult paint(ItemStack stack, BlockState state, Level level,
                                        BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof DyeItem dye) || dye.getDyeColor() == getColor()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        HolderLookup.Provider registries = level.registryAccess();
        BlockEntity previous = level.getBlockEntity(pos);
        CompoundTag data = previous == null ? null : previous.saveWithoutMetadata(registries);

        applyPaint(level, pos, paintedState(state, dye.getDyeColor()));

        BlockEntity fresh = level.getBlockEntity(pos);
        if (data != null && fresh != null) {
            fresh.loadWithComponents(data, registries);
            if (fresh instanceof SmartBlockEntity smart) {
                smart.notifyUpdate();
            } else {
                fresh.setChanged();
            }
        }

        if (!player.isCreative()) stack.shrink(1);
        level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        return ItemInteractionResult.SUCCESS;
    }
}
