package com.lycoris.loconautics.content.transmission;

import com.lycoris.loconautics.content.frequency.BindFrequencyScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TransmissionScreen
        extends BindFrequencyScreen<TransmissionBlockEntity, TransmissionMenu> {

    public TransmissionScreen(TransmissionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }
}