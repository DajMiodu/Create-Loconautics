package com.lycoris.loconautics.content.cerisene;

import net.minecraft.world.level.block.state.BlockState;

public final class CeriseneTint {

    private CeriseneTint() {}

    public static int color(BlockState state) {
        float t = state.getValue(CeriseneBlock.HEAT) / (float) CeriseneTuning.MAX_HEAT;
        return lerp(CeriseneTuning.PINK_TINT, CeriseneTuning.RED_TINT, t);
    }

    private static int lerp(int from, int to, float t) {
        int r = channel(from >> 16, to >> 16, t);
        int g = channel(from >> 8, to >> 8, t);
        int b = channel(from, to, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, float t) {
        int a = from & 0xFF;
        int b = to & 0xFF;
        return Math.round(a + (b - a) * t);
    }
}
