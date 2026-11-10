package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.foundation.utility.IInteractionChecker;
import net.minecraft.world.entity.player.Player;

public record PanelSlotTarget(PanelBlockEntity panel, PanelSlot slot) implements IInteractionChecker {

    public boolean hasFrequency() {
        return panel.getPart(slot) instanceof RedstoneLinkedPart;
    }

    @Override
    public boolean canPlayerUse(Player player) {
        return !panel.isRemoved() && panel.hasPart(slot);
    }
}
