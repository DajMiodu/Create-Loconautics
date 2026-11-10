package com.lycoris.loconautics.client.gui.gauge;

import com.lycoris.loconautics.foundation.ui.PoseScissor;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class GaugePartColumn {

    public interface SlotRenderer {
        void render(GuiGraphics g, int index, int cx, int cy, int box);
    }

    private static final int TOP_BOTTOM_SIZE = 22;
    private static final int CENTER_SIZE = 24;
    private static final float SCROLL_MILLIS = 140f;

    private final int centerX;
    private final int topY;
    private final int midY;
    private final int bottomY;
    private final int count;
    private final int slotInset;
    private final SlotRenderer slotRenderer;

    private int selected;

    private float scrollFrom;
    private long scrollStart;

    public GaugePartColumn(int centerX, int topY, int midY, int bottomY, int count, int slotInset,
                           SlotRenderer slotRenderer) {
        this.centerX = centerX;
        this.topY = topY;
        this.midY = midY;
        this.bottomY = bottomY;
        this.count = count;
        this.slotInset = slotInset;
        this.slotRenderer = slotRenderer;
    }

    public int selected() {
        return selected;
    }

    public void setSelected(int selected) {
        this.selected = ((selected % count) + count) % count;
    }

    private int wrapped(int offset) {
        return ((selected + offset) % count + count) % count;
    }

    private void step(int direction) {
        scrollFrom = Mth.clamp(scrollOffset() + direction, -1.5f, 1.5f);
        scrollStart = Util.getMillis();
        selected = wrapped(direction);
    }

    private float scrollOffset() {
        float t = Mth.clamp((Util.getMillis() - scrollStart) / SCROLL_MILLIS, 0f, 1f);
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        return scrollFrom * (1f - eased);
    }

    private int slotY(float position) {
        return Math.round(position < 0f
                ? midY + position * (midY - topY)
                : midY + position * (bottomY - midY));
    }

    public void render(GuiGraphics g) {
        float scroll = scrollOffset();
        if (scroll == 0f) {
            slotRenderer.render(g, wrapped(-1), centerX, topY, 14 - slotInset * 2);
            slotRenderer.render(g, selected, centerX, midY, 18 - slotInset * 2);
            slotRenderer.render(g, wrapped(1), centerX, bottomY, 14 - slotInset * 2);
            return;
        }
        int half = CENTER_SIZE / 2;
        PoseScissor.enable(g, centerX - half, topY - TOP_BOTTOM_SIZE / 2, centerX + half, bottomY + TOP_BOTTOM_SIZE / 2);
        for (int offset = -2; offset <= 2; offset++) {
            float position = offset + scroll;
            if (Math.abs(position) > 1.6f) continue;
            float size = Mth.lerp(Math.min(1f, Math.abs(position)), 18f, 14f) - slotInset * 2;
            slotRenderer.render(g, wrapped(offset), centerX, slotY(position), Math.round(size));
        }
        g.disableScissor();
    }

    public boolean hovered(double mx, double my) {
        return insideColumn(mx, my);
    }

    public boolean mouseScrolled(double mx, double my, double sy) {
        if (!insideColumn(mx, my) || sy == 0) return false;
        step(sy > 0 ? -1 : 1);
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (hit(mx, my, topY, TOP_BOTTOM_SIZE)) {
            step(-1);
            return true;
        }
        if (hit(mx, my, bottomY, TOP_BOTTOM_SIZE)) {
            step(1);
            return true;
        }
        return hit(mx, my, midY, CENTER_SIZE);
    }

    private boolean insideColumn(double mx, double my) {
        double half = CENTER_SIZE / 2.0;
        double top = topY - TOP_BOTTOM_SIZE / 2.0;
        double bottom = bottomY + TOP_BOTTOM_SIZE / 2.0;
        return mx >= centerX - half && mx < centerX + half && my >= top && my < bottom;
    }

    private boolean hit(double mx, double my, int cy, int size) {
        double half = size / 2.0;
        return mx >= centerX - half && mx < centerX + half && my >= cy - half && my < cy + half;
    }
}
