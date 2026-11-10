package com.lycoris.loconautics.mixin.client.station;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.AbstractStationScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// a sable cart is now a stations imminent train on the server, stationscreenmixin already draws it, so create must not draw it twice
@Mixin(value = AbstractStationScreen.class, remap = false)
public abstract class AbstractStationScreenImminentMixin {

    @Inject(method = "getImminent", at = @At("RETURN"), cancellable = true)
    private void loconautics$hideSableShadow(CallbackInfoReturnable<Train> cir) {
        Train imminent = cir.getReturnValue();
        if (imminent != null && SableTrainClientRegistry.isTrainId(imminent.id)) {
            cir.setReturnValue(null);
        }
    }
}
