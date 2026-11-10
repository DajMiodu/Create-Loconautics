package com.lycoris.loconautics.content.brakes.pull;

public final class PullBrakeMotion {

    private PullBrakeMotion() {}

    public static final float WHEEL_PIVOT_X = 8f / 16f;
    public static final float WHEEL_PIVOT_Y = 8f / 16f;
    public static final float WHEEL_PIVOT_Z = 13f / 16f;

    public static final float WHEEL_FULL_STROKE_DEGREES = 360f;

    public static final float CHAIN_TRAVEL_PIXELS = 11f;

    public static final float CHAIN_STEP_PIXELS = 5f;

    private static final float CHAIN_REST_LIFT_PIXELS = 1f;

    public static final int FIRST_LINK = -3;

    public static final int LAST_LINK = 0;

    public static float wheelDegrees(float pull) {
        return -pull * WHEEL_FULL_STROKE_DEGREES;
    }

    public static float linkOffsetPixels(int link, float pull) {
        return CHAIN_REST_LIFT_PIXELS - ((link + 1) * CHAIN_STEP_PIXELS + pull * CHAIN_TRAVEL_PIXELS);
    }

    public static boolean linkSpawned(int link, float pull) {
        return linkOffsetPixels(link, pull) < CHAIN_REST_LIFT_PIXELS;
    }
}
