package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.content.frequency.BindFrequencyScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AnalogControllerScreen
        extends BindFrequencyScreen<AnalogControllerBlockEntity, AnalogControllerMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "loconautics", "textures/gui/frequency/frequency_bind_4.png");

    public AnalogControllerScreen(AnalogControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, TEXTURE, 214, 153, 214, 153, 19, 158, 119, 157, 179);
    }
}
