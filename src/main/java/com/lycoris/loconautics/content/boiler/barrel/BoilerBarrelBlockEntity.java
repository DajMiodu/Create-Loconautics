package com.lycoris.loconautics.content.boiler.barrel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BoilerBarrelBlockEntity extends BlockEntity {

    private int storedWater;

    public BoilerBarrelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void storeWater(int millibuckets) {
        if (millibuckets <= 0) return;
        storedWater += millibuckets;
        setChanged();
    }

    public int takeWater(int millibuckets) {
        int taken = Math.min(storedWater, Math.max(0, millibuckets));
        if (taken > 0) {
            storedWater -= taken;
            setChanged();
        }
        return taken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (storedWater > 0) tag.putInt("StoredWater", storedWater);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedWater = tag.getInt("StoredWater");
    }
}
