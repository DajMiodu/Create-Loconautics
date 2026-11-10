package com.lycoris.loconautics.foundation.smoke;

public enum SmokeStyle {

    TRAIL(0.12F, 0.25F, 4.5F, 160, 0.30F, 0.050F, 26.0F, 0.75F, 0.90F, 0.016F, 0.60F, 0.50F, 0.07F),
    CHUFF(0.30F, 0.50F, 6.0F, 200, 0.30F, 0.050F, 26.0F, 0.75F, 0.90F, 0.016F, 0.85F, 0.45F, 0.07F),
    LEAK(0.06F, 0.12F, 1.1F, 90, 0.06F, 0.030F, 14.0F, 1.00F, 0.92F, 0.009F, 0.55F, 0.70F, 0.07F),
    STEAM(0.06F, 0.12F, 1.7F, 50, 0.03F, 0.020F, 12.0F, 1.00F, 0.82F, 0.030F, 0.75F, 1.00F, 0.14F),
    EXHAUST(0.06F, 0.12F, 1.4F, 80, 0.05F, 0.025F, 12.0F, 1.00F, 0.92F, 0.012F, 0.28F, 0.95F, 0.10F);

    public final float startMin;
    public final float startMax;
    public final float endSize;
    public final int lifetime;
    public final float rise;
    public final float riseFloor;
    public final float coolTicks;
    public final float inherit;
    public final float drag;
    public final float turbulence;
    public final float alpha;
    public final float soot;
    public final float fadeIn;

    SmokeStyle(float startMin, float startMax, float endSize, int lifetime, float rise, float riseFloor,
               float coolTicks, float inherit, float drag, float turbulence, float alpha, float soot,
               float fadeIn) {
        this.startMin = startMin;
        this.startMax = startMax;
        this.endSize = endSize;
        this.lifetime = lifetime;
        this.rise = rise;
        this.riseFloor = riseFloor;
        this.coolTicks = coolTicks;
        this.inherit = inherit;
        this.drag = drag;
        this.turbulence = turbulence;
        this.alpha = alpha;
        this.soot = soot;
        this.fadeIn = fadeIn;
    }

    public static SmokeStyle byId(int id) {
        SmokeStyle[] all = values();
        return all[Math.floorMod(id, all.length)];
    }
}
