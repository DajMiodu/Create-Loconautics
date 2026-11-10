package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

public class BufferRenderer extends SmartBlockEntityRenderer<BufferBlockEntity> {

    private static final float PX = 1.0f / 16.0f;
    private static final float ROD_BACK = 16.0f;
    private static final float ROD_LENGTH = 6.0f;

    public BufferRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BufferBlockEntity be, float partialTicks, PoseStack ps,
                              MultiBufferSource bufferSource, int light, int overlay) {
        BlockState state = be.getBlockState();
        DyeColor color = ((BufferBlock) state.getBlock()).getColor();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
        float extension = be.shownExtensionPixels(partialTicks);
        float blockstateYaw = (be.facing().toYRot() + 180.0f) % 360.0f;

        ps.pushPose();
        ps.translate(0.5f, state.getValue(CopycatPlacement.PLACEMENT).blocks(), 0.5f);
        ps.mulPose(Axis.YP.rotationDegrees(-blockstateYaw));
        ps.translate(-0.5f, 0.0f, -0.5f);

        ps.pushPose();
        ps.translate(0.0f, 0.0f, -Math.min(extension, be.size().sleeveOutPixels()) * PX);
        CachedBuffers.partial(LoconauticsPartialModels.BUFFER_SLEEVES.get(color), state).light(light).renderInto(ps, buffer);
        ps.popPose();

        ps.pushPose();
        ps.translate(0.0f, 0.0f, ROD_BACK * PX);
        ps.scale(1.0f, 1.0f, (ROD_LENGTH + extension) / ROD_LENGTH);
        ps.translate(0.0f, 0.0f, -ROD_BACK * PX);
        CachedBuffers.partial(LoconauticsPartialModels.BUFFER_RODS.get(color), state).light(light).renderInto(ps, buffer);
        ps.popPose();

        ps.translate(0.0f, 0.0f, -extension * PX);
        CachedBuffers.partial(LoconauticsPartialModels.BUFFER_HEADS.get(color), state).light(light).renderInto(ps, buffer);
        ps.popPose();
    }
}
