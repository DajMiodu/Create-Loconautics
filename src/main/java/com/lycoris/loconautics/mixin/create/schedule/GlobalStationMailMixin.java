package com.lycoris.loconautics.mixin.create.schedule;

import com.lycoris.loconautics.content.trainmail.CreateTrainMail;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlobalStation.class)
public abstract class GlobalStationMailMixin {

    @Shadow
    public abstract Train getPresentTrain();

    @Inject(method = "runMailTransfer", at = @At("HEAD"), cancellable = true)
    private void loconautics$filteredTransfer(CallbackInfo ci) {
        Train train = getPresentTrain();
        if (train != null) {
            CreateTrainMail.transfer((GlobalStation) (Object) this, train);
        }
        ci.cancel();
    }
}
