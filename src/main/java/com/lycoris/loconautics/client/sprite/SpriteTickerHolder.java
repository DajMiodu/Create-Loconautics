package com.lycoris.loconautics.client.sprite;

import net.minecraft.client.renderer.texture.SpriteTicker;
import org.jetbrains.annotations.Nullable;

public interface SpriteTickerHolder {

    // mixin refuses direct references into its own package, so this lives here. whatever
    @Nullable
    SpriteTicker loconautics$getTicker();

    void loconautics$setTicker(SpriteTicker ticker);
}
