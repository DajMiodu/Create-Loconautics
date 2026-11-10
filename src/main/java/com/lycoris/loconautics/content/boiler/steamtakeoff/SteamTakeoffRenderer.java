package com.lycoris.loconautics.content.boiler.steamtakeoff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class SteamTakeoffRenderer extends KineticBlockEntityRenderer<SteamTakeoffBlockEntity> {

    public SteamTakeoffRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SteamTakeoffBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        Direction.Axis axis = getRotationAxisOf(be);
        BlockState shaftState = shaft(axis);
        SuperByteBuffer shaftBuffer = CachedBuffers.block(KINETIC_BLOCK, shaftState);
        renderRotatingBuffer(be, shaftBuffer, ms, buffer.getBuffer(RenderType.cutoutMipped()), light);
    }
}
