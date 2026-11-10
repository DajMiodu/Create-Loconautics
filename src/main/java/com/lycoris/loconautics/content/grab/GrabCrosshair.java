package com.lycoris.loconautics.content.grab;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class GrabCrosshair {

    public static final LayeredDraw.Layer OVERLAY = GrabCrosshair::render;

    private static final ResourceLocation ICON = LoconauticsConstants.id("textures/gui/grab/crosshair.png");
    private static final int ICON_W = 12;
    private static final int ICON_H = 9;

    private GrabCrosshair() {
    }

    public static boolean replacesCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        return GrabClientHandler.grabbing() && mc.options.getCameraType().isFirstPerson() && !mc.options.hideGui;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!replacesCrosshair()) {
            return;
        }
        int x = (graphics.guiWidth() - ICON_W) / 2;
        int y = (graphics.guiHeight() - ICON_H) / 2;
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        graphics.blit(ICON, x, y, 0.0f, 0.0f, ICON_W, ICON_H, ICON_W, ICON_H);
        RenderSystem.defaultBlendFunc();
    }
}
