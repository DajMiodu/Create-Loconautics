package com.lycoris.loconautics.mixin.client;

import com.lycoris.loconautics.compat.crn.CrnCartDisplays;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity", remap = false)
public abstract class AdvancedDisplayCartMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void loconautics$tickOnCart(CallbackInfo ci) {
        CrnCartDisplays.tick((BlockEntity) (Object) this);
    }
}
