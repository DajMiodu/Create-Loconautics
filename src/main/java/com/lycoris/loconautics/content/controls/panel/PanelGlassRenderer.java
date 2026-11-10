package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;

public final class PanelGlassRenderer {

    private PanelGlassRenderer() {}

    public static void hinge(PoseStack ms, PanelGlass glass, float degrees) {
        ms.translate(glass.pivotX(), glass.pivotY(), glass.pivotZ());
        ms.mulPose(Axis.XP.rotationDegrees(degrees));
        ms.translate(-glass.pivotX(), -glass.pivotY(), -glass.pivotZ());
    }

    public static void render(PanelGlass glass, float degrees, PoseStack ms, MultiBufferSource buffers,
                              int light, BlockState state) {
        ms.pushPose();
        hinge(ms, glass, degrees);
        CachedBuffers.partial(LoconauticsPartialModels.glass(glass), state)
                .light(light)
                .renderInto(ms, buffers.getBuffer(RenderType.translucent()));
        ms.popPose();
    }
}
