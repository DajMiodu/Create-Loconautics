package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class HatchRenderer extends SafeBlockEntityRenderer<FluidHatchBlockEntity> {

    private static final float OPEN_DEGREES = 105.0f;

    public HatchRenderer(BlockEntityRendererProvider.Context context) {
    }

    protected PartialModel baseModel(FluidHatchBlockEntity be) {
        return LoconauticsPartialModels.HATCH_FLUID_BASE;
    }

    protected PartialModel lidModel(FluidHatchBlockEntity be) {
        return LoconauticsPartialModels.HATCH_FLUID_LID;
    }

    @Override
    protected void renderSafe(FluidHatchBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        float angle = ease(be.lid.getValue(partialTicks)) * OPEN_DEGREES;
        Direction facing = be.getBlockState().getValue(FluidHatchBlock.FACING);

        ms.pushPose();
        if (be.getBlockState().getValue(FluidHatchBlock.RAISED)) {
            Direction dome = facing.getOpposite();
            ms.translate(dome.getStepX() * FluidHatchBlock.BARREL_BULGE,
                    dome.getStepY() * FluidHatchBlock.BARREL_BULGE,
                    dome.getStepZ() * FluidHatchBlock.BARREL_BULGE);
        }

        SuperByteBuffer base = CachedBuffers.partial(baseModel(be), be.getBlockState());
        orient(base, facing);
        base.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));

        SuperByteBuffer lid = CachedBuffers.partial(lidModel(be), be.getBlockState());
        orient(lid, facing);
        int hinge = be.getBlockState().getValue(FluidHatchBlock.LID_HINGE);
        float hingeDeg = hinge * 90.0f + (facing == Direction.SOUTH ? 180.0f : 0.0f);
        if (hingeDeg != 0) {
            lid.center().rotateYDegrees(hingeDeg).uncenter();
        }
        lid.translate(0, 4 / 16f, 13 / 16f)
                .rotateXDegrees(angle)
                .translate(0, -4 / 16f, -13 / 16f);
        lid.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
    }

    private static void orient(SuperByteBuffer buf, Direction facing) {
        buf.center();
        switch (facing) {
            case UP -> buf.rotateXDegrees(180);
            case NORTH -> buf.rotateXDegrees(90);
            case SOUTH -> buf.rotateXDegrees(270);
            case WEST -> buf.rotateYDegrees(90).rotateXDegrees(90);
            case EAST -> buf.rotateYDegrees(270).rotateXDegrees(90);
            default -> { }
        }
        buf.uncenter();
    }

    private static float ease(float t) {
        return t < 0.5f ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t);
    }
}
