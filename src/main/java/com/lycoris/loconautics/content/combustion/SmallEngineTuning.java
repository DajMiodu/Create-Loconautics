package com.lycoris.loconautics.content.combustion;

public final class SmallEngineTuning {

    private SmallEngineTuning() {}

    public static final float TOP_RPM = 256f;

    public static final float SU_PER_ENGINE = 8192f;

    public static final float SU_PER_RPM = SU_PER_ENGINE / TOP_RPM;

    public static final int MAX_TANDEM = 3;

    public static final int TANK_CAPACITY = 1000;

    public static final int OFF_SIGNAL = 15;

    public static final int SLOWEST_SIGNAL = 14;

    private static final int SPEED_STEPS = 16;

    public static boolean isOff(int signal) {
        return signal >= OFF_SIGNAL;
    }

    public static float speedFraction(int signal) {
        return isOff(signal) ? 0f : (SPEED_STEPS - Math.max(0, signal)) / (float) SPEED_STEPS;
    }
}
