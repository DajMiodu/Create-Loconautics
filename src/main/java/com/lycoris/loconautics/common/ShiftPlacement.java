package com.lycoris.loconautics.common;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;

public final class ShiftPlacement {

    private ShiftPlacement() {
    }

    public static Direction facing(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Player player = context.getPlayer();
        return player != null && player.isShiftKeyDown() ? facing.getOpposite() : facing;
    }
}
