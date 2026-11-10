package com.lycoris.loconautics.mixin.create.kinetic;

import com.lycoris.loconautics.trainphysics.actors.TrainActorControls;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticControlsSpeedMixin {

    @Inject(method = "getSpeed", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$stopWhenSwitchedOff(CallbackInfoReturnable<Float> cir) {
        if (TrainActorControls.isDisabled((BlockEntity) (Object) this)) {
            cir.setReturnValue(0f);
        }
    }
}
