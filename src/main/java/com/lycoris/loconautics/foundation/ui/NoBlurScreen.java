package com.lycoris.loconautics.foundation.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class NoBlurScreen extends Screen {

    protected NoBlurScreen(Component title) {
        super(title);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
