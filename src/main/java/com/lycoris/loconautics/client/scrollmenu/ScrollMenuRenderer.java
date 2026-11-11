package com.lycoris.loconautics.client.scrollmenu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

public final class ScrollMenuRenderer {

    public static final LayeredDraw.Layer OVERLAY = ScrollMenuRenderer::render;

    private static final int SLOT = 22;
    private static final int GAP = 4;
    private static final int NAME_GAP = 16;
    private static final int DROP = 22;
    private static final int YELLOW_DROP = 6;

    private ScrollMenuRenderer() {
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ScrollMenu s = ScrollMenuHandler.current();
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null) return;

        float pt = deltaTracker.getGameTimeDeltaPartialTick(true);
        float alpha = s.alpha.getValue(pt);
        if (alpha <= 0.01f) return;
        float scale = s.scale.getValue(pt);

        draw(graphics, s, alpha, scale, graphics.guiWidth() / 2, graphics.guiHeight() - 59 - NAME_GAP - SLOT + DROP);
    }

    public static void draw(GuiGraphics graphics, ScrollMenu s, float alpha, float scale, int cx, int rowY) {
        Minecraft mc = Minecraft.getInstance();
        int total = SLOT * 3 + GAP * 2;
        int startX = cx - total / 2;
        int[] slotX = {startX, startX + SLOT + GAP, startX + (SLOT + GAP) * 2};

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(cx, rowY + SLOT / 2f, 0);
        ms.scale(scale, scale, 1);
        ms.translate(-cx, -(rowY + SLOT / 2f), 0);

        Component sel = s.entries.get(s.selected).name();
        graphics.drawString(mc.font, sel, cx - mc.font.width(sel) / 2,
                rowY - NAME_GAP - mc.font.lineHeight + YELLOW_DROP, withAlpha(0xFFD700, alpha), true);

        int[] slotIndex = {s.wrapped(-1), s.selected, s.wrapped(1)};
        for (int i = 0; i < 3; i++) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            AllGuiTextures.TOOLBELT_SLOT.render(graphics, slotX[i], rowY);
            if (i == 1) {
                AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, slotX[i] - 1, rowY - 1);
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.renderItem(s.entries.get(slotIndex[i]).icon(), slotX[i] + 3, rowY + 3);
        }

        ms.popPose();
    }

    private static int withAlpha(int rgb, float a) {
        int na = Math.max(0, Math.min(255, Math.round(255 * a)));
        return (na << 24) | (rgb & 0xFFFFFF);
    }
}
