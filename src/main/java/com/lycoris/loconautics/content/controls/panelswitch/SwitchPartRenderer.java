package com.lycoris.loconautics.content.controls.panelswitch;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.style.PanelBulbRenderer;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panel.PanelBodyRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

public class SwitchPartRenderer {

    public static final float ROCK_DEGREES = 22.5f;

    public static final float PIVOT_X = 4f / 16f;

    public static final float PIVOT_Y = 2f / 16f;

    public static final float PIVOT_Z = 4.00328f / 16f;

    public static final int PADDLE_STRIDE_X = 3;

    public static final int PADDLE_STRIDE_Y = 5;

    public static void render(float fraction, PoseStack ms, MultiBufferSource buffers,
                              int light, BlockState state, int body, int knobColor, int style, int options, float bulbGlow) {
        TextureAtlasSprite sheet = LoconauticsSpriteShifts.GAUGE_SWITCH.getTarget();
        float pixelU = (sheet.getU1() - sheet.getU0()) / 64f;
        float pixelV = (sheet.getV1() - sheet.getV0()) / 64f;

        if (PanelStyles.SWITCH.isOn(options, PanelStyles.SWITCH_PLATE)) {
            PanelBodyRenderer.renderSlimPlate(ms, buffers.getBuffer(RenderType.cutout()), light, state);
        }

        PanelBodyRenderer.renderSlimBody(ms, buffers.getBuffer(RenderType.cutout()), light, state, body);

        ms.pushPose();
        ms.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        ms.mulPose(Axis.XP.rotationDegrees(ROCK_DEGREES * fraction));
        ms.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        CachedBuffers.partial(LoconauticsPartialModels.SWITCH_PADDLE, state)
                .light(light)
                .shiftUVScrolling(LoconauticsSpriteShifts.GAUGE_SWITCH,
                        pixelU * PADDLE_STRIDE_X * (knobColor % 5), pixelV * PADDLE_STRIDE_Y * (knobColor / 5))
                .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        ms.popPose();

        if (PanelStyles.SWITCH.isOn(options, PanelStyles.SWITCH_LIGHT)) {
            PanelBulbRenderer.render(ms, buffers, light, state, PanelBulbRenderer.glowFor(bulbGlow),
                    LoconauticsPartialModels.SWITCH_BULB);
        }
    }
}
