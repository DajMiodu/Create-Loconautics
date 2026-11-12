package com.lycoris.loconautics.foundation.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.Map;
import java.util.WeakHashMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SmokeSpriteBounds {

    private static final int FILTER_PAD = 2;
    private static final Map<TextureAtlasSprite, SmokeSpriteBounds> CACHE = new WeakHashMap<>();

    public final float left;
    public final float right;
    public final float top;
    public final float bottom;

    private SmokeSpriteBounds(float left, float right, float top, float bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    public static SmokeSpriteBounds of(TextureAtlasSprite sprite) {
        return CACHE.computeIfAbsent(sprite, SmokeSpriteBounds::measure);
    }

    private static SmokeSpriteBounds measure(TextureAtlasSprite sprite) {
        NativeImage image = sprite.contents().getOriginalImage();
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getPixelRGBA(x, y) >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            return new SmokeSpriteBounds(0.0F, 1.0F, 0.0F, 1.0F);
        }
        return new SmokeSpriteBounds(
                Math.max(0, minX - FILTER_PAD) / (float) width,
                Math.min(width, maxX + 1 + FILTER_PAD) / (float) width,
                Math.max(0, minY - FILTER_PAD) / (float) height,
                Math.min(height, maxY + 1 + FILTER_PAD) / (float) height);
    }
}
