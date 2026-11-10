package com.lycoris.loconautics.content.controls.panel.style;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class PanelBulbRenderer {

    private static final float LIT_THRESHOLD = 0.125f;

    private PanelBulbRenderer() {}

    public static float glowFor(float outputFraction) {
        return outputFraction <= 0.01f ? 0f : 0.2f + 0.55f * outputFraction;
    }

    public static boolean isLit(float glow) {
        return glow > LIT_THRESHOLD;
    }

    public static int additiveLevel(float glow) {
        float curve = Mth.clamp(1f - 2f * (float) Math.pow(glow - 0.75f, 2), -1f, 1f);
        return (int) (200f * curve);
    }

    public static void render(PoseStack ms, MultiBufferSource buffers, int light, BlockState state,
                              float glow, PartialModel bulb) {
        boolean lit = isLit(glow);

        CachedBuffers.partial(bulb, state)
                .light(lit ? LightTexture.FULL_BRIGHT : light)
                .renderInto(ms, buffers.getBuffer(RenderType.translucent()));

        if (lit) {
            int color = additiveLevel(glow);
            CachedBuffers.partial(bulb, state)
                    .light(LightTexture.FULL_BRIGHT)
                    .color(color, color, color, 255)
                    .renderInto(ms, buffers.getBuffer(RenderTypes.additive()));
        }
    }
}
