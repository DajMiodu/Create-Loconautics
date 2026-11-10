package com.lycoris.loconautics.common.strand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

public final class StrandTemplates {

    private static int placing;

    private StrandTemplates() {
    }

    public static void beginPlacing() {
        placing++;
    }

    public static void endPlacing() {
        placing--;
    }

    public static boolean isPlacing() {
        return placing > 0;
    }

    public static CompoundTag record(UUID id, BlockEntity self, BlockPos farPos, CompoundTag data) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        BlockPos delta = farPos.subtract(self.getBlockPos());
        tag.putIntArray("Far", new int[]{delta.getX(), delta.getY(), delta.getZ()});
        tag.putString("FarBlock",
                BuiltInRegistries.BLOCK.getKey(self.getLevel().getBlockState(farPos).getBlock()).toString());
        tag.put("Data", data);
        return tag;
    }
}
