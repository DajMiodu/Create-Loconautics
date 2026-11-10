package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

public class ValvePartRenderer {

    public static final float PIVOT = 4f / 16f;

    public static final int CELL_STRIDE_X = 11;

    public static final int CELL_STRIDE_Y = 7;

    public static void render(float degrees, PoseStack ms, MultiBufferSource buffers,
                              int light, BlockState state, int wheelColor) {
        CachedBuffers.partial(LoconauticsPartialModels.VALVE_BASE, state)
                .light(light)
                .renderInto(ms, buffers.getBuffer(RenderType.cutout()));

        TextureAtlasSprite sheet = LoconauticsSpriteShifts.VALVE.getTarget();
        float pixelU = (sheet.getU1() - sheet.getU0()) / 64f;
        float pixelV = (sheet.getV1() - sheet.getV0()) / 64f;

        ms.pushPose();
        ms.translate(PIVOT, 0, PIVOT);
        ms.mulPose(Axis.YP.rotationDegrees(-degrees));
        ms.translate(-PIVOT, 0, -PIVOT);
        CachedBuffers.partial(LoconauticsPartialModels.VALVE_HANDLE, state)
                .light(light)
                .shiftUVScrolling(LoconauticsSpriteShifts.VALVE,
                        pixelU * CELL_STRIDE_X * (wheelColor % 5), pixelV * CELL_STRIDE_Y * (wheelColor / 5))
                .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        ms.popPose();
    }
}
