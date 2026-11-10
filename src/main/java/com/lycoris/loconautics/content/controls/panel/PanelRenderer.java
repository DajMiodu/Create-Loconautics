package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPartRenderer;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity.Gauge;
import com.lycoris.loconautics.content.controls.panelgauge.GaugePartRenderer;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panellever.LeverPartRenderer;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class PanelRenderer<T extends PanelBlockEntity> implements BlockEntityRenderer<T> {

    public PanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T panel, float partialTick, PoseStack ms, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = panel.getBlockState();

        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart part = panel.getPart(slot);
            if (part == null) continue;

            float animation = panel.animation(slot).getValue(partialTick);

            ms.pushPose();
            applyPartTransform(ms, state);
            float offX = panel.isCentered() ? (float) PanelBlock.CENTER_OFFSET : slot.modelX;
            float offZ = panel.isCentered() ? (float) PanelBlock.CENTER_OFFSET : slot.modelZ;
            ms.translate(offX / 16f, 0, offZ / 16f);

            switch (part) {
                case Gauge gauge -> GaugePartRenderer.render(gauge, panel, slot, animation, partialTick,
                        ms, buffers, packedLight, state);
                case ValvePart valve -> ValvePartRenderer.render(animation, ms, buffers, packedLight, state,
                        valve.wheelColor());
                case ButtonPart button -> ButtonPartRenderer.render(animation, ms, buffers, packedLight, state,
                        button.body(), button.capColor(), button.style(), button.shownOptions(), panel.bulbGlow(slot, partialTick));
                case LeverPart lever -> LeverPartRenderer.render(animation, ms, buffers, packedLight, state,
                        lever.body(), lever.knobColor(), lever.style(), lever.shownOptions(),
                        panel.bulbGlow(slot, partialTick));
                case com.lycoris.loconautics.content.controls.panelswitch.SwitchPart flip ->
                        com.lycoris.loconautics.content.controls.panelswitch.SwitchPartRenderer.render(
                                animation, ms, buffers, packedLight, state, flip.body(), flip.knobColor(), flip.style(), flip.shownOptions(),
                        panel.bulbGlow(slot, partialTick));
                default -> {
                }
            }

            PanelGlass glass = part.glass();
            if (glass != null) {
                PanelGlassRenderer.render(glass, panel.glassAngle(slot), ms, buffers, packedLight, state);
            }

            ms.popPose();
        }
    }

    protected void applyPartTransform(PoseStack ms, BlockState state) {
        placePanel(ms, state);
    }

    public static void placePanel(PoseStack ms, BlockState state) {
        AttachFace face = state.getValue(PanelBlock.FACE);
        Direction facing = state.getValue(PanelBlock.FACING);

        int x = face == AttachFace.WALL ? 90 : face == AttachFace.CEILING ? 180 : 0;
        int yaw = ((facing.get2DDataValue() + 2) % 4) * 90 + (face == AttachFace.CEILING ? 180 : 0);

        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-Mth.wrapDegrees(yaw)));
        ms.mulPose(Axis.XP.rotationDegrees(-x));
        ms.translate(-0.5, -0.5, -0.5);
    }
}
