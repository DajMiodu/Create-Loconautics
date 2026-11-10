package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panellever.LeverPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class LeverModelViewer extends BodyColorModelViewer {

    private static final LeverModelViewer ICON = new LeverModelViewer();

    private static final float PREVIEW_FRACTION = 0.5f;

    private static final float PREVIEW_GLOW = 1f;

    @Override
    protected float iconDrop() {
        return 0.125f;
    }

    @Override
    protected float iconScale() {
        return 0.9f;
    }

    @Override
    protected void draw(PoseStack pose, MultiBufferSource buffers) {
        LeverPartRenderer.render(PREVIEW_FRACTION, pose, buffers, LightTexture.FULL_BRIGHT, state, body, color, style, options, PREVIEW_GLOW);
    }

    public static void renderLeverIcon(GuiGraphics g, BlockState state, int body, int knobColor, int style, int options,
                                       int cx, int cy, int box) {
        ICON.renderIconWith(g, state, body, knobColor, style, options, cx, cy, box);
    }
}
