package com.lycoris.loconautics.client.gui.gauge;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBodyRenderer;
import com.lycoris.loconautics.client.gui.selector.PartModelViewer;
import com.lycoris.loconautics.content.controls.panel.style.PanelBulbRenderer;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeNeedle;
import com.lycoris.loconautics.content.controls.panelgauge.GaugePartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

public class GaugeModelViewer extends PartModelViewer {

    private static final GaugeModelViewer ICON = new GaugeModelViewer();

    private static final float PREVIEW_GLOW = 1f;

    private int background;
    private int front;
    private int needleColor;
    private int options = PanelStyles.GAUGE.defaultOptions();

    public void setBackground(int background) {
        this.background = background;
    }

    public void setFront(int front) {
        this.front = front;
    }

    public void setNeedleColor(int needleColor) {
        this.needleColor = needleColor;
    }

    public void setOptions(int options) {
        this.options = options;
    }

    @Override
    protected float iconDrop() {
        return 0.06f;
    }

    @Override
    protected void draw(PoseStack pose, MultiBufferSource buffers) {
        drawGauge(pose, buffers, state, background, front, needleColor, options);
    }

    public static void drawGauge(PoseStack pose, MultiBufferSource buffers, BlockState state,
                                 int background, int front, int needleColor, int options) {
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        int light = LightTexture.FULL_BRIGHT;
        TextureAtlasSprite sheet = LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE.getTarget();
        float pixelU = (sheet.getU1() - sheet.getU0()) / 64f;
        float pixelV = (sheet.getV1() - sheet.getV0()) / 64f;
        float step = pixelU * 8f;
        float stepV = pixelV * 8f;

        if (PanelStyles.GAUGE.isOn(options, PanelStyles.GAUGE_PLATE)) {
            PanelBodyRenderer.renderPlate(pose, vc, light, state);
        }

        CachedBuffers.partial(LoconauticsPartialModels.GAUGE_BACK, state)
                .light(light)
                .renderInto(pose, vc);
        CachedBuffers.partial(LoconauticsPartialModels.GAUGE_FACE, state)
                .light(light)
                .shiftUVScrolling(LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE, step * (background % 6), stepV * (background / 6))
                .renderInto(pose, vc);
        PanelBodyRenderer.renderBody(pose, vc, light, state, GaugePartRenderer.bodyForFront(front));

        GaugeNeedle style = GaugeNeedle.forBackground(background);
        pose.pushPose();
        if (style.slides()) {
            pose.translate(0, 0, 0.5f * style.slidePixels() / 16f);
        }
        CachedBuffers.partial(GaugePartRenderer.partialFor(style), state)
                .light(light)
                .shiftUVScrolling(LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE,
                        pixelU * style.colorOffsetU(needleColor),
                        pixelV * style.colorOffsetV(needleColor))
                .renderInto(pose, vc);
        pose.popPose();

        if (PanelStyles.GAUGE.isOn(options, PanelStyles.GAUGE_LIGHT)) {
            PanelBulbRenderer.render(pose, buffers, light, state, PanelBulbRenderer.glowFor(PREVIEW_GLOW),
                    LoconauticsPartialModels.GAUGE_BULB);
        }
    }

    public static void renderGaugeIcon(GuiGraphics g, BlockState state, int background, int front,
                                       int needleColor, int options, int cx, int cy, int box) {
        ICON.setState(state);
        ICON.setBackground(background);
        ICON.setFront(front);
        ICON.setNeedleColor(needleColor);
        ICON.setOptions(options);
        ICON.renderIcon(g, cx, cy, box);
    }
}
