package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.trainphysics.actors.TrainActorControls;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HarvesterMovementBehaviour.class, remap = false)
public abstract class HarvesterControlsDisableMixin {

    // a harvester has no kinetic speed, so the getspeed gate cannot reach it
    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$skipWhenSwitchedOff(MovementContext context, BlockPos pos, CallbackInfo ci) {
        if (context == null || context.contraption != null || context.world == null) {
            return;
        }
        if (TrainActorControls.isDisabled(context.world, context.localPos, context.state)) {
            ci.cancel();
        }
    }
}
