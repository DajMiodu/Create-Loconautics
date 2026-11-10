package com.lycoris.loconautics.mixin.client;

import com.lycoris.loconautics.common.strand.ClientStrands;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientSubLevelContainer.class, remap = false)
public abstract class ClientSubLevelContainerCableMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void loconautics$tickCableInterpolation(CallbackInfo ci) {
        ClientStrands.tickAll(
                ((ClientSubLevelContainer) (Object) this).getInterpolation().getTickPointer());
    }
}
