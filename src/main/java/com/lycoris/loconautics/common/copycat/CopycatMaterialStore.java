package com.lycoris.loconautics.common.copycat;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CopycatMaterialStore {

    private final BlockEntity owner;

    private BlockState material = AllBlocks.COPYCAT_BASE.getDefaultState();
    private ItemStack consumed = ItemStack.EMPTY;

    public CopycatMaterialStore(BlockEntity owner) {
        this.owner = owner;
    }

    public BlockState material() {
        return material;
    }

    public boolean hasCustomMaterial() {
        return !AllBlocks.COPYCAT_BASE.has(material);
    }

    public ItemStack consumed() {
        return consumed;
    }

    public void setMaterial(BlockState state) {
        material = state;
        push();
    }

    public void setConsumed(ItemStack stack) {
        consumed = stack.copyWithCount(1);
        push();
    }

    public void reset() {
        material = AllBlocks.COPYCAT_BASE.getDefaultState();
        consumed = ItemStack.EMPTY;
        push();
    }

    public boolean cycleMaterial() {
        if (material.hasProperty(TrapDoorBlock.HALF) && material.getOptionalValue(TrapDoorBlock.OPEN).orElse(false)) {
            setMaterial(material.cycle(TrapDoorBlock.HALF));
        } else if (material.hasProperty(BlockStateProperties.FACING)) {
            setMaterial(material.cycle(BlockStateProperties.FACING));
        } else if (material.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            setMaterial(material.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    material.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise()));
        } else if (material.hasProperty(BlockStateProperties.AXIS)) {
            setMaterial(material.cycle(BlockStateProperties.AXIS));
        } else if (material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            setMaterial(material.cycle(BlockStateProperties.HORIZONTAL_AXIS));
        } else if (material.hasProperty(BlockStateProperties.LIT)) {
            setMaterial(material.cycle(BlockStateProperties.LIT));
        } else {
            return false;
        }
        return true;
    }

    private void push() {
        owner.setChanged();
        if (owner instanceof SmartBlockEntity smart) {
            smart.sendData();
        }
        Level level = owner.getLevel();
        if (level != null) {
            BlockState own = owner.getBlockState();
            level.sendBlockUpdated(owner.getBlockPos(), own, own, 3);
        }
    }

    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Material", NbtUtils.writeBlockState(material));
        if (!consumed.isEmpty()) {
            tag.put("Consumed", consumed.save(registries));
        }
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        BlockState previous = material;
        material = tag.contains("Material")
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Material"))
                : AllBlocks.COPYCAT_BASE.getDefaultState();
        consumed = tag.contains("Consumed")
                ? ItemStack.parseOptional(registries, tag.getCompound("Consumed"))
                : ItemStack.EMPTY;
        if (clientPacket && previous != material) {
            redraw();
        }
    }

    private void redraw() {
        owner.requestModelDataUpdate();
        Level level = owner.getLevel();
        if (level == null) {
            return;
        }
        BlockState own = owner.getBlockState();
        level.sendBlockUpdated(owner.getBlockPos(), own, own, 16);
    }
}
