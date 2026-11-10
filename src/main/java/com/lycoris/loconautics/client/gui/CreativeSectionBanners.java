package com.lycoris.loconautics.client.gui;

import com.lycoris.loconautics.common.creative.CreativeSection;
import com.lycoris.loconautics.common.creative.CreativeSections;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

public final class CreativeSectionBanners {

    private static final int WIDTH = 162;
    private static final int HEIGHT = 18;
    private static final int VISIBLE_ROWS = 5;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private CreativeSectionBanners() {
    }

    public static void render(CreativeModeInventoryScreen screen, GuiGraphics graphics) {
        for (CreativeSection section : CreativeSections.all()) {
            int visibleRow = section.row() - CreativeTabBanner.currentRow;
            if (section.row() < 0 || visibleRow < 0 || visibleRow >= VISIBLE_ROWS) {
                continue;
            }
            PoseStack ps = graphics.pose();
            ps.pushPose();
            ps.translate(screen.getGuiLeft() + 8, screen.getGuiTop() + 17 + visibleRow * HEIGHT, 0.0f);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            graphics.blitSprite(section.sprite(), 0, 0, WIDTH, HEIGHT);
            graphics.drawString(Minecraft.getInstance().font, section.title(), 5, 5, TEXT_COLOR, true);
            RenderSystem.disableDepthTest();
            ps.popPose();
        }
    }
}
