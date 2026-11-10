package com.lycoris.loconautics.content.controls.panellever;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.style.PanelBulbRenderer;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyle;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panel.PanelBodyRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class LeverPartRenderer {

    public static final float OFF_DEGREES = -55f;

    public static final float ON_DEGREES = 55f;

    public static final float PIVOT_X = 4f / 16f;

    public static final float PIVOT_Y = 1.5f / 16f;

    public static final float PIVOT_Z = 4f / 16f;

    public static final int KNOB_STRIDE_X = 5;

    public static final int KNOB_STRIDE_Y = 5;

    public static void render(float fraction, PoseStack ms, MultiBufferSource buffers,
                              int light, BlockState state, int body, int knobColor, int style, int options, float bulbGlow) {
        TextureAtlasSprite sheet = LoconauticsSpriteShifts.GAUGE_LEVER.getTarget();
        float pixelU = (sheet.getU1() - sheet.getU0()) / 64f;
        float pixelV = (sheet.getV1() - sheet.getV0()) / 64f;

        PanelStyle panelStyle = PanelStyles.LEVER.get(style);
        PanelStyle.ModelKit kit = panelStyle.kit();
        LoconauticsPartialModels.StyleKit partials = kit == null ? null
                : LoconauticsPartialModels.LEVER_STYLE_KITS.get(kit.name());
        int knobStrideX = kit == null ? KNOB_STRIDE_X : kit.knobStrideX();
        int knobStrideY = kit == null ? KNOB_STRIDE_Y : kit.knobStrideY();
        if (PanelStyles.LEVER.isOn(options, PanelStyles.LEVER_PLATE)) {
            PanelBodyRenderer.renderSlimPlate(ms, buffers.getBuffer(RenderType.cutout()), light, state);
        }

        PanelBodyRenderer.renderSlimBody(ms, buffers.getBuffer(RenderType.cutout()), light, state, body);

        if (partials != null && partials.base() != null) {
            CachedBuffers.partial(partials.base(), state)
                    .light(light)
                    .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        }

        ms.pushPose();
        ms.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        ms.mulPose(Axis.XP.rotationDegrees(Mth.lerp(fraction, OFF_DEGREES, ON_DEGREES)));
        ms.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        CachedBuffers.partial(partials == null ? LoconauticsPartialModels.LEVER_HANDLE : partials.handle(), state)
                .light(light)
                .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        CachedBuffers.partial(partials == null ? LoconauticsPartialModels.LEVER_KNOB : partials.knob(), state)
                .light(light)
                .shiftUVScrolling(LoconauticsSpriteShifts.GAUGE_LEVER,
                        pixelU * knobStrideX * (knobColor % 5), pixelV * knobStrideY * (knobColor / 5))
                .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        ms.popPose();

        if (PanelStyles.LEVER.isOn(options, PanelStyles.LEVER_LIGHT)) {
            PanelBulbRenderer.render(ms, buffers, light, state, PanelBulbRenderer.glowFor(bulbGlow),
                    LoconauticsPartialModels.LEVER_BULB);
        }
    }
}
