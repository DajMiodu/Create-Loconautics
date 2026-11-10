package com.lycoris.loconautics.mixin.create.train;

import com.simibubi.create.infrastructure.config.CTrains;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = CTrains.class, remap = false)
public abstract class CTrainsMaxTrackLengthMixin {

    @ModifyArgs(method = "<init>", at = @At(value = "INVOKE",
            target = "i(IIILjava/lang/String;[Ljava/lang/String;)Lnet/createmod/catnip/config/ConfigBase$ConfigInt;"))
    private void loconautics$widenTrackPlacement(Args args) {
        if ("maxTrackPlacementLength".equals(args.<String>get(3))) {
            args.set(0, 128);
            args.set(2, 1024);
        }
    }
}
