package com.lycoris.loconautics.content.brakes.pull;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public class PullBrakeRenderer extends SafeBlockEntityRenderer<PullBrakeBlockEntity> {

    public PullBrakeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(PullBrakeBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) return;

        BlockState state = be.getBlockState();
        String color = ((PullBrakeBlock) state.getBlock()).colorName();
        float pull = be.pull.getValue(partialTicks);
        float yaw = 180.0f - state.getValue(HorizontalDirectionalBlock.FACING).toYRot();
        VertexConsumer vb = buffer.getBuffer(RenderType.cutout());

        SuperByteBuffer wheel = oriented(LoconauticsPartialModels.PULL_BRAKE_WHEELS.get(color), state, yaw);
        wheel.translate(PullBrakeMotion.WHEEL_PIVOT_X, PullBrakeMotion.WHEEL_PIVOT_Y, PullBrakeMotion.WHEEL_PIVOT_Z)
                .rotateZDegrees(PullBrakeMotion.wheelDegrees(pull))
                .translate(-PullBrakeMotion.WHEEL_PIVOT_X, -PullBrakeMotion.WHEEL_PIVOT_Y,
                        -PullBrakeMotion.WHEEL_PIVOT_Z);
        wheel.light(light).renderInto(ms, vb);

        for (int link = PullBrakeMotion.FIRST_LINK; link <= PullBrakeMotion.LAST_LINK; link++) {
            if (!PullBrakeMotion.linkSpawned(link, pull)) continue;
            SuperByteBuffer chain = oriented(LoconauticsPartialModels.PULL_BRAKE_CHAINS.get(color), state, yaw);
            chain.translate(0, PullBrakeMotion.linkOffsetPixels(link, pull) / 16f, 0);
            chain.light(light).renderInto(ms, vb);
        }
    }

    private static SuperByteBuffer oriented(PartialModel model, BlockState state, float yaw) {
        SuperByteBuffer part = CachedBuffers.partial(model, state);
        part.center().rotateYDegrees(yaw).uncenter();
        return part;
    }
}
