package com.lycoris.loconautics.mixin.client.bogey;

import com.lycoris.loconautics.client.BogeyShaftDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BogeyBlockEntityRenderer.class, remap = false)
public abstract class BogeyShaftRenderMixin {

    @Inject(method = "renderSafe", at = @At("TAIL"))
    private void loconautics$drawShaft(AbstractBogeyBlockEntity be, float partialTicks, PoseStack ms,
                                       MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        BogeyShaftDraw.draw(be, ms, buffer, light);
    }
}
