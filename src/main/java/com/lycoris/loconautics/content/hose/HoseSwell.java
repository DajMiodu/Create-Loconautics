package com.lycoris.loconautics.content.hose;

public final class HoseSwell {

    public static final float MAX_SWELL_PX = 0.8f;
    public static final double PHASE_STEP = 0.25;
    public static final float WAVE_PERIOD_TICKS = 27.0f;

    private HoseSwell() {
    }

    public static float wavePx(double phase) {
        return (float) (0.5 * (1.0 - Math.cos(phase)));
    }

    public static float frontPx(double fill, double cumDist) {
        float local = (float) Math.max(0.0, Math.min(1.0, fill - cumDist));
        return local * local * (3.0f - 2.0f * local);
    }

    public static float scale(float px) {
        return (3.0f + MAX_SWELL_PX * px) / 3.0f;
    }

    public static float travelling(double ticks, double distFromEntry) {
        double phase = ticks * Math.PI * 2.0 / WAVE_PERIOD_TICKS - distFromEntry * PHASE_STEP * Math.PI * 2.0;
        return scale(wavePx(phase));
    }
}
