package com.lycoris.loconautics.content.steelcable;

import com.simibubi.create.AllItems;
import net.minecraft.world.item.ItemStack;

public final class SteelCableWrench {

    private SteelCableWrench() {
    }

    public static boolean isWrench(ItemStack stack) {
        return AllItems.WRENCH.isIn(stack);
    }
}
