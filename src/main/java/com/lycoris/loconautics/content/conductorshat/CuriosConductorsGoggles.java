package com.lycoris.loconautics.content.conductorshat;

import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public final class CuriosConductorsGoggles {

    private CuriosConductorsGoggles() {}

    public static boolean isWorn(Player player) {
        ICuriosItemHandler handler = player.getCapability(CuriosCapability.INVENTORY);
        if (handler == null) {
            return false;
        }
        for (ICurioStacksHandler stacks : handler.getCurios().values()) {
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                if (stacks.getStacks().getStackInSlot(slot).getItem() instanceof ConductorsGogglesItem) {
                    return true;
                }
            }
        }
        return false;
    }
}
