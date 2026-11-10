package com.lycoris.loconautics.mixin.create.track;

import com.lycoris.loconautics.common.railway.PointOverrides;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TravellingPoint.class, remap = false)
public abstract class TravellingPointPointsMixin {

    @Inject(method = "steer(Lcom/simibubi/create/content/trains/entity/TravellingPoint$SteerDirection;Lnet/minecraft/world/phys/Vec3;)Lcom/simibubi/create/content/trains/entity/TravellingPoint$ITrackSelector;",
            at = @At("RETURN"), cancellable = true)
    private void loconautics$points(TravellingPoint.SteerDirection direction, Vec3 upNormal,
                                    CallbackInfoReturnable<TravellingPoint.ITrackSelector> cir) {
        cir.setReturnValue(PointOverrides.wrap(cir.getReturnValue()));
    }
}
