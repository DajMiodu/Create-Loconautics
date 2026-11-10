package com.lycoris.loconautics.mixin.client.bogey;

import com.lycoris.loconautics.client.BogeyShaftDraw;
import com.lycoris.loconautics.common.BogeyGroundDrop;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.visual.BogeyRenderFrame;
import com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BogeyBlockEntityRenderer.class, remap = false)
public abstract class BogeyRenderPoseMixin {

    @Inject(method = "renderSafe", at = @At("HEAD"), cancellable = true)
    private void loconautics$railPose(AbstractBogeyBlockEntity be, float partialTicks, PoseStack ms,
                                      MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        if (!(be.getBlockState().getBlock() instanceof AbstractBogeyBlock<?> block)) {
            return;
        }
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        if (sub == null || !SableTrainClientRegistry.isTrain(sub.getUniqueId())) {
            renderGrounded(be, block, partialTicks, ms, buffer, light, overlay, ci);
            return;
        }

        BogeyRenderFrame.Frame frame = BogeyRenderFrame.of(be, sub, partialTicks);

        ms.pushPose();
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.translate(frame.bodyCounter().x, frame.bodyCounter().y, frame.bodyCounter().z);
        ms.mulPose(frame.local());
        ms.translate(frame.sway().x, frame.sway().y, frame.sway().z);
        be.getStyle().render(block.getSize(), partialTicks, ms, buffer, light, overlay,
                be.getVirtualAngle(partialTicks), be.getBogeyData(), false);
        ms.popPose();

        BogeyWheelAnimator.frame(be);
        BogeyShaftDraw.draw(be, ms, buffer, light);
        ci.cancel();
    }

    @Unique
    private static void renderGrounded(AbstractBogeyBlockEntity be, AbstractBogeyBlock<?> block, float partialTicks,
                                       PoseStack ms, MultiBufferSource buffer, int light, int overlay,
                                       CallbackInfo ci) {
        float drop = BogeyGroundDrop.of(be);
        if (drop <= 0.0f) {
            return;
        }
        ms.pushPose();
        ms.translate(0.5f, 0.5f - drop, 0.5f);
        if (be.getBlockState().getValue(AbstractBogeyBlock.AXIS) == Direction.Axis.X) {
            ms.mulPose(Axis.YP.rotationDegrees(90));
        }
        be.getStyle().render(block.getSize(), partialTicks, ms, buffer, light, overlay,
                be.getVirtualAngle(partialTicks), be.getBogeyData(), false);
        ms.popPose();
        BogeyShaftDraw.draw(be, ms, buffer, light);
        ci.cancel();
    }
}
