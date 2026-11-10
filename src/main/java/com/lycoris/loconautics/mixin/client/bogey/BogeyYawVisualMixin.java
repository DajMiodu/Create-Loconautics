package com.lycoris.loconautics.mixin.client.bogey;

import com.lycoris.loconautics.common.BogeyGroundDrop;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.visual.BogeyRenderFrame;
import com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyBlockEntityVisual;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BogeyBlockEntityVisual.class, remap = false)
public abstract class BogeyYawVisualMixin {

    @Unique
    private static final float LOCONAUTICS_BOGEY_DROP = 1.5078125f;

    @Shadow
    private PoseStack poseStack;

    @Shadow
    private BogeyVisual bogey;

    @Unique
    private AbstractBogeyBlockEntity loconautics$blockEntity;

    @Unique
    private final Matrix4f loconautics$base = new Matrix4f();
    @Unique
    private boolean loconautics$baseCaptured = false;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void loconautics$onConstructed(dev.engine_room.flywheel.api.visualization.VisualizationContext ctx,
                                           AbstractBogeyBlockEntity blockEntity, float partialTick, CallbackInfo ci) {
        this.loconautics$blockEntity = blockEntity;
    }

    @Inject(method = "updateBogey", at = @At("HEAD"), cancellable = true)
    private void loconautics$railUpdate(float partialTick, CallbackInfo ci) {
        if (!this.loconautics$baseCaptured) {
            this.loconautics$base.set(this.poseStack.last().pose());
            this.loconautics$baseCaptured = true;
        }

        AbstractBogeyBlockEntity be = this.loconautics$blockEntity;
        if (be == null || this.bogey == null) {
            return;
        }

        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        BogeyRenderFrame.Frame frame = BogeyRenderFrame.of(be, sub, partialTick);

        float cx = this.loconautics$base.m30();
        float cy = this.loconautics$base.m31() + LOCONAUTICS_BOGEY_DROP;
        float cz = this.loconautics$base.m32();

        this.poseStack.last().pose().identity();
        this.poseStack.last().normal().identity();
        this.poseStack.translate(cx, cy, cz);
        if (sub == null || !SableTrainClientRegistry.isTrain(sub.getUniqueId())) {
            this.poseStack.translate(0.0f, -BogeyGroundDrop.of(be), 0.0f);
        }
        this.poseStack.translate(frame.bodyCounter().x, frame.bodyCounter().y, frame.bodyCounter().z);
        this.poseStack.mulPose(frame.local());
        this.poseStack.translate(frame.sway().x, frame.sway().y, frame.sway().z);
        this.poseStack.translate(0.0f, -LOCONAUTICS_BOGEY_DROP, 0.0f);

        CompoundTag bogeyData = be.getBogeyData();
        float angle = be.getVirtualAngle(partialTick);
        this.bogey.update(bogeyData, angle, this.poseStack);
        BogeyWheelAnimator.frame(be);
        ci.cancel();
    }
}
