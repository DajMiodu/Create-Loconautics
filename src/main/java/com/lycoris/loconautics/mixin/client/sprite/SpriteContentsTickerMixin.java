package com.lycoris.loconautics.mixin.client.sprite;

import com.lycoris.loconautics.client.sprite.SpriteTickerHolder;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteTicker;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpriteContents.class, remap = false)
public class SpriteContentsTickerMixin implements SpriteTickerHolder {

    @Unique
    @Nullable
    private SpriteTicker loconautics$ticker;

    @Override
    @Nullable
    public SpriteTicker loconautics$getTicker() {
        return loconautics$ticker;
    }

    @Override
    public void loconautics$setTicker(SpriteTicker ticker) {
        loconautics$ticker = ticker;
    }

    @Inject(method = "createTicker", at = @At("RETURN"))
    private void loconautics$captureTicker(CallbackInfoReturnable<SpriteTicker> cir) {
        loconautics$setTicker(cir.getReturnValue());
    }
}
