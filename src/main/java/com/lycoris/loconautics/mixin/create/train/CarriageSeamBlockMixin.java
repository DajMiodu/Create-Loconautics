package com.lycoris.loconautics.mixin.create.train;

import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.content.trains.entity.Carriage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Carriage.class, remap = false)
public abstract class CarriageSeamBlockMixin {

    @Inject(method = "travel", at = @At("HEAD"))
    private void loconautics$beginTravel(CallbackInfoReturnable<Double> cir) {
        RailSeams.beginCreateTrainTravel();
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void loconautics$endTravel(CallbackInfoReturnable<Double> cir) {
        RailSeams.endCreateTrainTravel();
    }
}
