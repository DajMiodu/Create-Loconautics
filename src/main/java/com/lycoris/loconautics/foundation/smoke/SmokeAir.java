package com.lycoris.loconautics.foundation.smoke;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class SmokeAir {

    private static final double NOISE_SCALE = 0.22;
    private static final double NOISE_TIME = 0.012;
    private static final double WIND_TIME = 0.0006;
    private static final float WIND_MIN = 0.004F;
    private static final float WIND_MAX = 0.014F;

    private SmokeAir() {
    }

    public static void wind(Level level, double y, float[] out) {
        double t = level.getGameTime() * WIND_TIME;
        double angle = noise(t, 3.7, 0.0, 0.0) * Mth.TWO_PI * 2.0;
        double gust = noise(t * 6.0, 9.1, 0.0, 0.0);
        double altitude = Mth.clamp(0.7 + (y - 64.0) * 0.01, 0.5, 1.6);
        double rain = 1.0 + level.getRainLevel(1.0F) * 0.6;
        double speed = Mth.lerp(gust, WIND_MIN, WIND_MAX) * altitude * rain;
        out[0] = (float) (Math.cos(angle) * speed);
        out[1] = (float) (Math.sin(angle) * speed);
    }

    public static void turbulence(Level level, double x, double y, double z, float[] out) {
        double t = level.getGameTime() * NOISE_TIME;
        double sx = x * NOISE_SCALE;
        double sy = y * NOISE_SCALE;
        double sz = z * NOISE_SCALE;
        out[0] = (float) (noise(sx + t, sy, sz, 11.0) * 2.0 - 1.0);
        out[1] = (float) (noise(sx, sy + t, sz, 23.0) * 2.0 - 1.0);
        out[2] = (float) (noise(sx, sy, sz + t, 37.0) * 2.0 - 1.0);
    }

    private static double noise(double x, double y, double z, double seed) {
        int xi = Mth.floor(x);
        int yi = Mth.floor(y);
        int zi = Mth.floor(z);
        double fx = fade(x - xi);
        double fy = fade(y - yi);
        double fz = fade(z - zi);
        int s = (int) seed;
        double c000 = hash(xi, yi, zi, s);
        double c100 = hash(xi + 1, yi, zi, s);
        double c010 = hash(xi, yi + 1, zi, s);
        double c110 = hash(xi + 1, yi + 1, zi, s);
        double c001 = hash(xi, yi, zi + 1, s);
        double c101 = hash(xi + 1, yi, zi + 1, s);
        double c011 = hash(xi, yi + 1, zi + 1, s);
        double c111 = hash(xi + 1, yi + 1, zi + 1, s);
        double x00 = Mth.lerp(fx, c000, c100);
        double x10 = Mth.lerp(fx, c010, c110);
        double x01 = Mth.lerp(fx, c001, c101);
        double x11 = Mth.lerp(fx, c011, c111);
        return Mth.lerp(fz, Mth.lerp(fy, x00, x10), Mth.lerp(fy, x01, x11));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 362437;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
