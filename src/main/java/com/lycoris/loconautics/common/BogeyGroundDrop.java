package com.lycoris.loconautics.common;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BogeyGroundDrop {

    private static final String KEY = "LoconauticsGrounded";
    public static final float DROP = 5.0f / 16.0f;
    public static final VoxelShape RAISED_SHAPE = Shapes.or(Block.box(0, 7, 0, 16, 16, 16), Block.box(2, 5, 2, 14, 7, 14));
    public static final VoxelShape SHAPE =Shapes.or(Block.box(0, 2, 0, 16, 11, 16), Block.box(2, 0, 2, 14, 2, 14));

    private BogeyGroundDrop() {
    }

    public static float of(BlockEntity blockEntity) {
        return blockEntity instanceof AbstractBogeyBlockEntity bogey && bogey.getBogeyData().getBoolean(KEY)
                ? DROP : 0.0f;
    }

    public static void set(AbstractBogeyBlockEntity bogey, boolean grounded) {
        if (grounded) {
            bogey.getBogeyData().putBoolean(KEY, true);
        } else if (bogey.getBogeyData().contains(KEY)) {
            bogey.getBogeyData().remove(KEY);
        } else {
            return;
        }
        bogey.setBogeyStyle(bogey.getStyle());
    }
}
