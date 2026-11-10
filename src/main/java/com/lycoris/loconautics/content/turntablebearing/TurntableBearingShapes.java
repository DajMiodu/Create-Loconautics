package com.lycoris.loconautics.content.turntablebearing;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

public final class TurntableBearingShapes {

    public static final VoxelShaper BEARING_ASSEMBLED =
            VoxelShaper.forDirectional(Block.box(0.0, 0.0, 0.0, 16.0, 11.9, 16.0), Direction.UP);

    public static final VoxelShaper PLATE =
            VoxelShaper.forDirectional(Block.box(0.0, 12.1, 0.0, 16.0, 16.0, 16.0), Direction.UP);

    public static final VoxelShaper PLATE_COLLISION =
            VoxelShaper.forDirectional(Block.box(3.0, 12.0, 3.0, 13.0, 16.0, 13.0), Direction.UP);

    private TurntableBearingShapes() {
    }
}
