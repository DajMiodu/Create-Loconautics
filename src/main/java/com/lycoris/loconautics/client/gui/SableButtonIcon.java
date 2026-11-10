package com.lycoris.loconautics.client.gui;

import com.lycoris.loconautics.LoconauticsConstants;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class SableButtonIcon implements ScreenElement {

    public static final SableButtonIcon INSTANCE = new SableButtonIcon();

    private static final ResourceLocation TEXTURE = LoconauticsConstants.id("textures/gui/widget/sable_button.png");
    private static final int WIDTH = 24;
    private static final int HEIGHT = 16;

    private SableButtonIcon() {
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(TEXTURE, x, y, 0, 0.0F, 0.0F, WIDTH, HEIGHT, WIDTH, HEIGHT);
    }
}
