package com.lycoris.loconautics.mixin.client.contraption;

import com.lycoris.loconautics.compat.create.patch.RollerAnimator;
import com.lycoris.loconautics.compat.create.patch.RollerWheelRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.contraptions.actors.roller.RollerRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = RollerRenderer.class, remap = false)
public abstract class RollerRendererMixin {

    @Inject(method = "renderSafe", at = @At("HEAD"), cancellable = true)
    private void loconautics$frameRollerAnimator(RollerBlockEntity be, float partialTicks,
                                                 PoseStack ms, MultiBufferSource buffer,
                                                 int light, int overlay, CallbackInfo ci) {
        RollerAnimator.frame(be);
        if (RollerWheelRender.render(be, partialTicks, ms, buffer, light, overlay)) {
            ci.cancel();
        }
    }
}
