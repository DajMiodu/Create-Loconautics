package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.gui.gauge.GaugePartColumn;
import com.lycoris.loconautics.content.controls.panel.PanelPartLayout;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class SelectorPanel {

    public static final int PANEL_W = PanelPartLayout.WINDOW_W;
    public static final int PANEL_H = PanelPartLayout.WINDOW_H;

    public static final int LABEL_Y = 3;
    public static final int LABEL_W = 200;
    public static final int LABEL_BAR_H = 15;
    public static final int LABEL_TEXT_COLOR = 0xFF404040;

    public static final int VIEW_X = 34;
    public static final int VIEW_Y = 43;
    public static final int VIEW_SIZE = 92;

    public static final int TOGGLE_Y = 169;
    public static final int INVERT_X = 5;
    public static final int INVERT_Y = TOGGLE_Y;
    public static final int OPTION_X = 27;
    public static final int OPTION_Y = TOGGLE_Y;
    public static final int OPTION_STEP = 22;
    public static final int CONFIRM_X = 314;
    public static final int CONFIRM_Y = TOGGLE_Y;

    public static final int ARROW_SIZE = 10;
    public static final int STYLE_ARROW_Y = VIEW_Y + VIEW_SIZE / 2 - ARROW_SIZE / 2;
    public static final int STYLE_PREV_X = 20;
    public static final int STYLE_NEXT_X = 130;
    public static final int STYLE_DOTS_Y = VIEW_Y + VIEW_SIZE + 7;

    public static final int ROW_X = 151;
    public static final int ROW_Y = 23;

    public static final int SIGNAL_TEXT_X = 157;
    public static final int SIGNAL_TEXT_W = 118;
    public static final int SIGNAL_TEXT_Y = 34;
    public static final int SIGNAL_TEXT_COLOR = 0xFFFFFFFF;

    public static final int METRIC_X = 157;
    public static final int METRIC_Y = 29;
    public static final int METRIC_W = 160;

    public static final int SOLO_STRIP_X = 217;
    public static final int LEFT_STRIP_X = 185;
    public static final int RIGHT_STRIP_X = 249;

    public static final int TRIPLE_LEFT_STRIP_X = 165;
    public static final int TRIPLE_MID_STRIP_X = 213;
    public static final int TRIPLE_RIGHT_STRIP_X = 261;

    public static final int BUTTON_SIZE = 18;

    private static final ResourceLocation TEXTURE =
            LoconauticsConstants.id("textures/gui/selector/panel.png");
    private static final int SHEET_W = 512;
    private static final int SHEET_H = 256;

    private static final int BUTTON_U = 352;
    private static final int BUTTON_HOVER_U = 376;
    private static final int BUTTON_V = 4;
    private static final int GREEN_BUTTON_U = 420;
    private static final int GREEN_BUTTON_V = 28;

    private static final int ARROW_U = 352;
    private static final int ARROW_V = 150;
    private static final int ARROW_HOVER_OFFSET = 12;
    private static final int ARROW_GLYPH_U = 376;

    public static final int ROW_W = 172;
    public static final int ROW_H = 30;
    private static final int SIGNAL_ROW_U = 340;
    private static final int SIGNAL_ROW_V = 166;
    private static final int GAUGE_ROW_U = 340;
    private static final int GAUGE_ROW_V = 198;

    private static final int RENAME_U = 442;
    private static final int RENAME_HOVER_U = 457;
    private static final int RENAME_V = 4;
    private static final int RENAME_SIZE = 9;

    private static final int STRIP_U = 352;
    private static final int STRIP_V = 64;
    public static final int STRIP_W = 40;
    public static final int STRIP_H = 82;
    public static final int STRIP_Y = 72;

    private static final int SLOT_CENTER_X = 20;
    private static final int TOP_SLOT_Y = STRIP_Y + 14;
    private static final int MID_SLOT_Y = STRIP_Y + 40;
    private static final int BOTTOM_SLOT_Y = STRIP_Y + 66;

    private static final int CORNER_OVERHANG = 1;

    private static final int DOT_SIZE = 4;
    public static final int DOT_STEP = 8;
    private static final int DOT_HIT_HEIGHT = 10;

    private SelectorPanel() {}

    public static void blitPanel(GuiGraphics g, int left, int top) {
        g.blit(TEXTURE, left, top, 0, 0, PANEL_W, PANEL_H, SHEET_W, SHEET_H);
    }

    public static void blitStrip(GuiGraphics g, int left, int top, int stripX) {
        g.blit(TEXTURE, left + stripX, top + STRIP_Y, STRIP_U, STRIP_V, STRIP_W, STRIP_H, SHEET_W, SHEET_H);
    }

    public static void blitButton(GuiGraphics g, int x, int y, boolean hovered, boolean active) {
        if (active && !hovered) {
            g.blit(TEXTURE, x, y, GREEN_BUTTON_U, GREEN_BUTTON_V, BUTTON_SIZE, BUTTON_SIZE, SHEET_W, SHEET_H);
            return;
        }
        g.blit(TEXTURE, x, y, hovered ? BUTTON_HOVER_U : BUTTON_U, BUTTON_V, BUTTON_SIZE, BUTTON_SIZE,
                SHEET_W, SHEET_H);
    }

    public static void blitArrow(GuiGraphics g, int x, int y, boolean next, boolean hovered) {
        int offset = hovered ? ARROW_HOVER_OFFSET : 0;
        g.blit(TEXTURE, x, y, ARROW_U + offset, ARROW_V, ARROW_SIZE, ARROW_SIZE, SHEET_W, SHEET_H);
        int glyphU = ARROW_GLYPH_U + offset;
        if (!next) {
            g.blit(TEXTURE, x, y, glyphU, ARROW_V, ARROW_SIZE, ARROW_SIZE, SHEET_W, SHEET_H);
            return;
        }
        g.blit(TEXTURE, x, y, ARROW_SIZE, ARROW_SIZE, (float) (glyphU + ARROW_SIZE), (float) ARROW_V,
                -ARROW_SIZE, ARROW_SIZE, SHEET_W, SHEET_H);
    }

    public static void blitSignalRow(GuiGraphics g, int left, int top) {
        g.blit(TEXTURE, left + ROW_X, top + ROW_Y, SIGNAL_ROW_U, SIGNAL_ROW_V, ROW_W, ROW_H, SHEET_W, SHEET_H);
    }

    public static void blitRename(GuiGraphics g, int x, int y, boolean hovered) {
        g.blit(TEXTURE, x, y, hovered ? RENAME_HOVER_U : RENAME_U, RENAME_V, RENAME_SIZE, RENAME_SIZE,
                SHEET_W, SHEET_H);
    }

    public static void blitMetricRow(GuiGraphics g, int left, int top) {
        g.blit(TEXTURE, left + ROW_X, top + ROW_Y, GAUGE_ROW_U, GAUGE_ROW_V, ROW_W, ROW_H, SHEET_W, SHEET_H);
    }

    public static GaugePartColumn column(int left, int top, int stripX, int count,
                                         GaugePartColumn.SlotRenderer slotRenderer) {
        return new GaugePartColumn(left + stripX + SLOT_CENTER_X, top + TOP_SLOT_Y, top + MID_SLOT_Y,
                top + BOTTOM_SLOT_Y, count, 0, slotRenderer);
    }

    public static void renderViewer(GuiGraphics g, PartModelViewer viewer, int left, int top) {
        viewer.render(g, left + VIEW_X, top + VIEW_Y, VIEW_SIZE, VIEW_SIZE);
    }

    public static final int OVERLAY_Z_ABOVE_PREVIEW_MODEL = 800;

    public static void renderViewerOverlay(GuiGraphics g, Font font, PartModelViewer viewer, int left, int top) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z_ABOVE_PREVIEW_MODEL);
        AllIcons.I_REFRESH.render(g, resetIconX(left), resetIconY(top));
        int level = viewer.zoom();
        String zoom = level > 0 ? "+" + level : String.valueOf(level);
        g.drawCenteredString(font, zoom, left + VIEW_X + VIEW_SIZE + CORNER_OVERHANG - 8,
                cornerBottom(top) - 6 - font.lineHeight / 2, 0xFFD0D0D8);
        g.pose().popPose();
    }

    public static void renderColumnTooltip(GuiGraphics g, Font font, GaugePartColumn column,
                                           String labelKey, int mouseX, int mouseY) {
        if (!column.hovered(mouseX, mouseY)) return;
        g.renderComponentTooltip(font, java.util.List.of(
                net.minecraft.network.chat.Component.translatable(labelKey),
                net.minecraft.network.chat.Component.translatable("gui.loconautics.selector.scroll_hint")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), mouseX, mouseY);
    }

    public static void renderStyleDots(GuiGraphics g, int left, int top, int count, int selected, int hovered) {
        int x0 = dotsLeft(left, count) + (DOT_STEP - DOT_SIZE) / 2;
        int y = top + STYLE_DOTS_Y;
        for (int i = 0; i < count; i++) {
            int color = i == selected ? 0xFFFFFFFF : i == hovered ? 0xFFB0B0C0 : 0xFF5A5A68;
            int x = x0 + i * DOT_STEP;
            g.fill(x + 1, y, x + DOT_SIZE - 1, y + DOT_SIZE, color);
            g.fill(x, y + 1, x + DOT_SIZE, y + DOT_SIZE - 1, color);
        }
    }

    public static int styleDotAt(int left, int top, int count, double mouseX, double mouseY) {
        double y0 = top + STYLE_DOTS_Y - (DOT_HIT_HEIGHT - DOT_SIZE) / 2.0;
        if (mouseY < y0 || mouseY >= y0 + DOT_HIT_HEIGHT) return -1;
        double relative = mouseX - dotsLeft(left, count);
        if (relative < 0 || relative >= count * DOT_STEP) return -1;
        return (int) (relative / DOT_STEP);
    }

    private static int dotsLeft(int left, int count) {
        return left + VIEW_X + (VIEW_SIZE - count * DOT_STEP) / 2;
    }

    public static boolean clickedReset(PartModelViewer viewer, int left, int top,
                                       double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x = resetIconX(left);
        int y = resetIconY(top);
        if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16) return false;
        viewer.resetView();
        return true;
    }

    private static int resetIconX(int left) {
        return left + VIEW_X - CORNER_OVERHANG;
    }

    private static int cornerBottom(int top) {
        return top + VIEW_Y + VIEW_SIZE + CORNER_OVERHANG;
    }

    private static int resetIconY(int top) {
        return cornerBottom(top) - 16;
    }
}
