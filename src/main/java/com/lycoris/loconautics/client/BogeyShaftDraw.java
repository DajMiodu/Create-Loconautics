package com.lycoris.loconautics.client;

import com.lycoris.loconautics.content.bearingaxle.BogeyShaft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class BogeyShaftDraw {

    private BogeyShaftDraw() {
    }

    public static void draw(AbstractBogeyBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState state = be.getBlockState();
        if (!BogeyShaft.isShafted(state)
                || VisualizationManager.supportsVisualization(be.getLevel()) && VisualizationHelper.canVisualize(be)) {
            return;
        }
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        KineticBlockEntity end = BogeyShaft.endpoint(be.getLevel(), be.getBlockPos(), axis);
        SuperByteBuffer shaft = CachedBuffers.partialFacingVertical(AllPartialModels.SHAFT, state,
                Direction.get(Direction.AxisDirection.POSITIVE, axis));
        if (end == null) {
            shaft.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
            return;
        }
        float angle = KineticBlockEntityRenderer.getAngleForBe(end, be.getBlockPos(), axis);
        KineticBlockEntityRenderer.kineticRotationTransform(shaft, end, axis, angle, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
