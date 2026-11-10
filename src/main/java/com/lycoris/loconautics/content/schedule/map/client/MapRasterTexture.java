package com.lycoris.loconautics.content.schedule.map.client;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class MapRasterTexture {

    public static final ResourceLocation TEXTURE_LOC =
            LoconauticsConstants.id("advanced_schedule_map");
    private static final int PX_PER_CHUNK = 16;

    private final int gridChunks;
    private final int viewOriginX, viewOriginZ;
    private final int texSize;

    private DynamicTexture texture;
    private boolean textureValid;

    private volatile boolean rebuilding;
    private final AtomicReference<NativeImage> pending = new AtomicReference<>(null);

    public MapRasterTexture(int gridChunks, int viewOriginX, int viewOriginZ) {
        this.gridChunks = gridChunks;
        this.viewOriginX = viewOriginX;
        this.viewOriginZ = viewOriginZ;
        this.texSize = gridChunks * PX_PER_CHUNK;
    }

    public void rebuildAsync(Map<Long, byte[]> colors) {
        if (rebuilding) return;
        rebuilding = true;
        final int gc = gridChunks, vx = viewOriginX, vz = viewOriginZ, ts = texSize;
        final Map<Long, byte[]> snapshot = Map.copyOf(colors);
        CompletableFuture.runAsync(() -> {
            try {
                NativeImage img = new NativeImage(NativeImage.Format.RGBA, ts, ts, false);
                fill(img, gc, vx, vz, snapshot);
                NativeImage old = pending.getAndSet(img);
                if (old != null) old.close();
            } catch (Exception e) {
                LoconauticsConstants.LOGGER.error("map raster rebuild failed", e);
            } finally {
                rebuilding = false;
            }
        });
    }

    public void tick() {
        NativeImage img = pending.getAndSet(null);
        if (img == null) return;
        if (texture == null) {
            texture = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_LOC, texture);
        } else {
            NativeImage px = texture.getPixels();
            if (px != null) { px.copyFrom(img); texture.upload(); }
            img.close();
        }
        textureValid = true;
    }

    public void free() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_LOC);
            texture.close();
            texture = null;
        }
        NativeImage p = pending.getAndSet(null);
        if (p != null) p.close();
        textureValid = false;
    }

    private static void fill(NativeImage img, int gc, int vox, int voz, Map<Long, byte[]> colors) {
        for (int rz = 0; rz < gc; rz++) {
            int cz = voz + rz;
            for (int rx = 0; rx < gc; rx++) {
                int cx = vox + rx;
                byte[] cell = colors.get(key(cx, cz));
                int pxBase = rx * PX_PER_CHUNK, pyBase = rz * PX_PER_CHUNK;
                for (int bz = 0; bz < 16; bz++) {
                    for (int bx = 0; bx < 16; bx++) {
                        int abgr;
                        if (cell == null) {
                            abgr = 0;
                        } else {
                            int packed = cell[bz * 16 + bx] & 0xFF;
                            abgr = packed == 0 ? 0 : MapColor.getColorFromPackedId(packed);
                        }
                        img.setPixelRGBA(pxBase + bx, pyBase + bz, abgr);
                    }
                }
            }
        }
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    public boolean isTextureValid() { return textureValid; }
    public int pxPerChunk() { return PX_PER_CHUNK; }
    public int viewOriginX() { return viewOriginX; }
    public int viewOriginZ() { return viewOriginZ; }
    public int gridChunks() { return gridChunks; }
}
