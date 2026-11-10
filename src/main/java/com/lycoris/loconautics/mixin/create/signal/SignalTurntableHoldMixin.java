package com.lycoris.loconautics.mixin.create.signal;

import com.lycoris.loconautics.common.turntable.TurntableSignals;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SignalBlockEntity.class, remap = false)
public abstract class SignalTurntableHoldMixin {

    @Inject(method = "getReportedPower", at = @At("RETURN"), cancellable = true)
    private void loconautics$holdForTurntable(CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!cir.getReturnValueZ() && TurntableSignals.forcedRed(self.getLevel(), self.getBlockPos())) {
            cir.setReturnValue(true);
        }
    }
}
