package com.lycoris.loconautics.compat.create.patch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlock;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.redstone.link.LinkRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RollerWheelRender {

    private RollerWheelRender() {
    }

    public static boolean render(RollerBlockEntity be, float partialTicks, PoseStack ms,
                                 MultiBufferSource buffer, int light, int overlay) {
        Float angle = RollerAnimator.angle(be, partialTicks);
        if (angle == null) {
            return false;
        }
        BlockState blockState = be.getBlockState();
        if (!blockState.hasProperty(RollerBlock.FACING)) {
            return false;
        }

        FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
        LinkRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);

        Direction facing = blockState.getValue(RollerBlock.FACING);
        VertexConsumer vc = buffer.getBuffer(RenderType.cutoutMipped());

        ms.pushPose();
        ms.translate(0, -0.25, 0);
        SuperByteBuffer wheel = CachedBuffers.partial(AllPartialModels.ROLLER_WHEEL, blockState);
        wheel.translate(Vec3.atLowerCornerOf(facing.getNormal()).scale(17 / 16f));
        wheel.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing)), Direction.UP)
                .rotate(AngleHelper.rad(angle), Direction.WEST);
        wheel.translate(0, -.5, .5)
                .rotateYDegrees(90)
                .light(light)
                .renderInto(ms, vc);
        ms.popPose();

        CachedBuffers.partial(AllPartialModels.ROLLER_FRAME, blockState)
                .rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing) + 180), Direction.UP)
                .light(light)
                .renderInto(ms, vc);
        return true;
    }
}
