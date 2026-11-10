package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class AnalogControllerRenderer implements BlockEntityRenderer<AnalogControllerBlockEntity> {

    public AnalogControllerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(AnalogControllerBlockEntity be, float partialTick,
                       PoseStack ms, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(AnalogControllerBlock.FACING);
        float hAngleRad = (float) Math.toRadians(180.0f + AngleHelper.horizontalAngle(facing));
        int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());

        float animSpeed = be.animSpeed.getValue(partialTick);
        float animSteer = be.animSteer.getValue(partialTick);
        float animEquip = be.animEquip.getValue(partialTick);

        float slide = Mth.lerp(animEquip * animEquip, -0.15f, 0.05f);

        for (boolean speedLever : new boolean[]{true, false}) {
            float value = speedLever ? animSpeed : animSteer;
            float angle = Mth.clamp(speedLever ? value * 70.0f - 25.0f : value * 15.0f, -45.0f, 45.0f);

            ms.pushPose();
            ms.translate(0.5, 0.5, 0.5);
            ms.mulPose(Axis.YP.rotation(hAngleRad));
            ms.translate(-0.5, -0.5, -0.5);

            ms.translate(0.0, 0.25, 0.25);
            ms.translate(0.5, 0.5, 0.5);
            ms.mulPose(Axis.XP.rotationDegrees(angle - 45.0f));
            ms.translate(-0.5, -0.5, -0.5);
            ms.translate(0.0, slide, 0.0);
            ms.translate(0.5, 0.5, 0.5);
            ms.mulPose(Axis.XP.rotationDegrees(45.0f));
            ms.translate(-0.5, -0.5, -0.5);
            ms.translate(0.0, -0.375, -0.1875);
            ms.translate(speedLever ? 0.0f : 0.375f, 0.0, 0.0);

            CachedBuffers.partial(LoconauticsPartialModels.CONTROLS_LEVER, state)
                    .light(light)
                    .renderInto(ms, buffers.getBuffer(RenderType.solid()));
            ms.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AnalogControllerBlockEntity be) {
        return false;
    }
}
