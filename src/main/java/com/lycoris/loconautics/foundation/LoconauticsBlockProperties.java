package com.lycoris.loconautics.foundation;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class LoconauticsBlockProperties {

    private LoconauticsBlockProperties() {
    }

    public static BlockBehaviour.Properties metallic() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5f)
                .sound(SoundType.NETHERITE_BLOCK)
                .requiresCorrectToolForDrops();
    }
}
