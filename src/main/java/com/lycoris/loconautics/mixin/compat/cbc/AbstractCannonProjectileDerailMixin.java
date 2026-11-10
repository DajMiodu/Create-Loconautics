package com.lycoris.loconautics.mixin.compat.cbc;

import com.lycoris.loconautics.compat.cbc.CbcTrainCompat;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile.ImpactResult;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;

@Mixin(value = AbstractCannonProjectile.class, remap = false)
public abstract class AbstractCannonProjectileDerailMixin {

    @Inject(method = "onImpact", at = @At("TAIL"))
    private void loconautics$derailOnImpact(HitResult hitResult, ImpactResult impactResult,
                                            ProjectileContext projectileContext,
                                            CallbackInfoReturnable<Boolean> cir) {
        AbstractCannonProjectile self = (AbstractCannonProjectile) (Object) this;
        CbcTrainCompat.onShellImpact(self.level(), hitResult, projectileContext);
    }
}
