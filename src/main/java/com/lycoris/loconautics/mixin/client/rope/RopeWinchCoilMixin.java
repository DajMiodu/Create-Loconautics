package com.lycoris.loconautics.mixin.client.rope;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RopeWinchRenderer.class, remap = false)
public abstract class RopeWinchCoilMixin {

    @Inject(method = "renderComponents", at = @At("RETURN"))
    private void loconautics$renderSteelCableCoil(RopeWinchBlockEntity be, float partialTicks, PoseStack ms,
                                                  MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        SteelCableHolderBehaviour holder = be.getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (holder == null || holder.attachedStrandIds().isEmpty() || be.getRopeHolder().isAttached()) {
            return;
        }
        BlockState state = be.getBlockState();
        SuperByteBuffer coil = CachedBuffers.partial(LoconauticsPartialModels.STEEL_CABLE_WINCH_COIL, state);
        coil.light(light);
        Direction facing = state.getValue(DirectionalKineticBlock.FACING);
        boolean alongFirst = state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
        float speed = facing == Direction.DOWN
                ? (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1.0f : -1.0f)
                : (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE == alongFirst ? 1.0f : -1.0f);
        AbstractPulleyRenderer.scrollCoil(coil, LoconauticsSpriteShifts.STEEL_CABLE_WINCH_COIL,
                ((RopeWinchBlockEntityAccessor) be).loconautics$getClientAngle().getValue(partialTicks), speed);

        float zRotLast = alongFirst ^ facing.getAxis() == Direction.Axis.Z ? 90.0f : 0.0f;
        float yRot = AngleHelper.horizontalAngle(facing)
                + (alongFirst || facing.getAxis() != Direction.Axis.Y ? 0.0f : 90.0f);
        float zRot = facing == Direction.UP ? 270.0f : (facing == Direction.DOWN ? 90.0f : 0.0f);
        ms.pushPose();
        coil.rotateCentered((float) (zRot / 180.0f * Math.PI), Direction.SOUTH)
                .rotateCentered((float) (yRot / 180.0f * Math.PI), Direction.UP)
                .rotateCentered((float) (zRotLast / 180.0f * Math.PI), Direction.SOUTH)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }
}
