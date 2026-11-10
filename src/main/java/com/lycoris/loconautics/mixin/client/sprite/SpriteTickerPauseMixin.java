package com.lycoris.loconautics.mixin.client.sprite;

import com.lycoris.loconautics.client.sprite.PausableSpriteTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$Ticker", remap = false)
public class SpriteTickerPauseMixin implements PausableSpriteTicker {

    @Unique
    private boolean loconautics$playing = true;

    @Override
    public void loconautics$setPlaying(boolean playing) {
        loconautics$playing = playing;
    }

    @Override
    public boolean loconautics$isPlaying() {
        return loconautics$playing;
    }

    @Inject(method = "tickAndUpload", at = @At("HEAD"), cancellable = true)
    private void loconautics$freezeWhenPaused(int x, int y, CallbackInfo ci) {
        if (!loconautics$playing) {
            ci.cancel();
        }
    }
}
