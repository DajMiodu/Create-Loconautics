package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.content.frequency.BindFrequencyScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BearingAxleScreen
        extends BindFrequencyScreen<BearingAxleBlockEntity, BearingAxleMenu> {

    public BearingAxleScreen(BearingAxleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }
}
