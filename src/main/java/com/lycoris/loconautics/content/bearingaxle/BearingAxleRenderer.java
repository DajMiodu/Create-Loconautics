package com.lycoris.loconautics.content.bearingaxle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class BearingAxleRenderer extends KineticBlockEntityRenderer<BearingAxleBlockEntity> {

    public BearingAxleRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BearingAxleBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }
        BlockState state = be.getBlockState();
        Direction shaftFace = BearingAxleBlock.shaftFace(state);
        Direction.Axis axis = shaftFace.getAxis();

        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, shaftFace);
        kineticRotationTransform(shaft, be, axis, getAngleForBe(be, be.getBlockPos(), axis), light);
        shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
