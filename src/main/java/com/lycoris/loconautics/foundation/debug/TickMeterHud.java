package com.lycoris.loconautics.foundation.debug;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

public final class TickMeterHud {

    public static final LayeredDraw.Layer OVERLAY = TickMeterHud::render;

    private static final int MARGIN = 2;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int RED = 0xFFFF5555;
    private static final double FRAME_SMOOTHING = 0.05;

    private static double smoothFrameMs;

    private TickMeterHud() {
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getSingleplayerServer() == null || mc.options.hideGui) {
            return;
        }
        double gap = TickMeter.gapMs();
        int color = gap < TickMeter.YELLOW_GAP_MS ? GREEN : gap < TickMeter.RED_GAP_MS ? YELLOW : RED;
        double frameMs = mc.getFrameTimeNs() / 1_000_000.0;
        smoothFrameMs += (frameMs - smoothFrameMs) * FRAME_SMOOTHING;
        String text = String.format("TPS %.1f  gap %.1f ms  tick %.1f ms | FPS %d  frame %.1f ms",
                TickMeter.tps(), gap, TickMeter.workMs(), mc.getFps(), smoothFrameMs);
        g.drawString(mc.font, text, MARGIN, MARGIN, color, true);
    }
}
