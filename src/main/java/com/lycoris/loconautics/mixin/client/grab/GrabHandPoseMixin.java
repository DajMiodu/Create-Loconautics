package com.lycoris.loconautics.mixin.client.grab;

import com.lycoris.loconautics.content.grab.GrabHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class GrabHandPoseMixin {

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void loconautics$reachForGrab(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                          float equippedProgress, float swingProgress, HumanoidArm side,
                                          CallbackInfo ci) {
        GrabHandRenderer.apply(poseStack, side, equippedProgress);
    }
}
