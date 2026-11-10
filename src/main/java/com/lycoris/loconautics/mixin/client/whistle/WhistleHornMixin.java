package com.lycoris.loconautics.mixin.client.whistle;

import com.lycoris.loconautics.content.analogcontroller.WhistleHonks;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = WhistleBlockEntity.class, remap = false)
public abstract class WhistleHornMixin {

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private boolean loconautics$honk(boolean powered) {
        return powered || WhistleHonks.isHonking(((WhistleBlockEntity) (Object) this).getBlockPos());
    }
}
