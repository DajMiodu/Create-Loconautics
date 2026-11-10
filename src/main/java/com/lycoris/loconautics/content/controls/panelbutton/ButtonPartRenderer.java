package com.lycoris.loconautics.content.controls.panelbutton;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBodyRenderer;
import com.lycoris.loconautics.content.controls.panel.style.PanelBulbRenderer;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyle;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

public class ButtonPartRenderer {

    public static final float TRAVEL = 0.75f / 16f;

    public static final int CAP_STRIDE_X = 5;

    public static final int CAP_STRIDE_Y = 6;

    public record CapStyle(PartialModel cap, SpriteShiftEntry shift, int strideX, int strideY, boolean big) {
    }

    public static CapStyle capStyle(int style) {
        PanelStyle.ModelKit kit = PanelStyles.BUTTON.get(style).kit();
        if (kit == null) {
            return new CapStyle(LoconauticsPartialModels.BUTTON_CAP, LoconauticsSpriteShifts.GAUGE_BUTTON,
                    CAP_STRIDE_X, CAP_STRIDE_Y, false);
        }
        return new CapStyle(LoconauticsPartialModels.BUTTON_STYLE_CAPS.get(kit.name()),
                LoconauticsSpriteShifts.BIG_BUTTON, kit.knobStrideX(), kit.knobStrideY(), true);
    }

    public static void render(float pressed, PoseStack ms, MultiBufferSource buffers,
                              int light, BlockState state, int body, int capColor, int style, int options,
                              float bulbGlow) {
        CapStyle capStyle = capStyle(style);
        TextureAtlasSprite sheet = capStyle.shift().getTarget();
        float pixelU = (sheet.getU1() - sheet.getU0()) / 64f;
        float pixelV = (sheet.getV1() - sheet.getV0()) / 64f;

        if (PanelStyles.BUTTON.isOn(options, PanelStyles.BUTTON_PLATE)) {
            PanelBodyRenderer.renderPlate(ms, buffers.getBuffer(RenderType.cutout()), light, state);
        }
        if (capStyle.big()) {
            PanelBodyRenderer.renderBigBody(ms, buffers.getBuffer(RenderType.cutout()), light, state, body);
        } else {
            PanelBodyRenderer.renderBody(ms, buffers.getBuffer(RenderType.cutout()), light, state, body);
        }

        ms.pushPose();
        ms.translate(0, -pressed * TRAVEL, 0);
        CachedBuffers.partial(capStyle.cap(), state)
                .light(light)
                .shiftUVScrolling(capStyle.shift(),
                        pixelU * capStyle.strideX() * (capColor % 5), pixelV * capStyle.strideY() * (capColor / 5))
                .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        ms.popPose();

        if (PanelStyles.BUTTON.isOn(options, PanelStyles.BUTTON_LIGHT)) {
            PanelBulbRenderer.render(ms, buffers, light, state, PanelBulbRenderer.glowFor(bulbGlow),
                    LoconauticsPartialModels.GAUGE_BULB);
        }
    }
}
