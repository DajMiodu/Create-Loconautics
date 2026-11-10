package com.lycoris.loconautics.mixin.create.signal;

import com.lycoris.loconautics.common.railway.SignalOverrides;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SignalBoundary.class, remap = false)
public abstract class SignalBoundaryHoldMixin {

    @Inject(method = "isForcedRed(Z)Z", at = @At("HEAD"), cancellable = true)
    private void loconautics$computerHold(boolean primary, CallbackInfoReturnable<Boolean> cir) {
        if (SignalOverrides.held(((SignalBoundary) (Object) this).getId(), primary)) {
            cir.setReturnValue(true);
        }
    }
}
