package com.lycoris.loconautics.content.knuckle;

import net.minecraft.util.Mth;

public final class KnuckleTuning {

    public static final double DEFAULT_CONNECT_DISTANCE = 1.5;
    public static final double DEFAULT_MATE_OPEN_GAP = 2.5;
    public static final double DEFAULT_MATE_CLOSE_GAP = 2.05;
    public static final double DEFAULT_POSITION_LERP = 0.35;
    public static final double DEFAULT_SPRING_STIFFNESS = 20000.0;
    public static final double DEFAULT_MAX_BIAS_SPEED = 1.0;

    public static final double REST_LENGTH = 2.0;
    public static final int ENGAGE_TICKS = 8;
    public static final int ROTATE_LOCK_TICKS = 40;
    public static final int COUPLE_LOCK_TICKS = 60;

    public static final double MAX_ACCEL = 60.0;
    public static final double WATCH_REACH = 6.0;
    public static final double BREAK_DISTANCE = 8.0;
    public static final int MISSING_PARTNER_SCANS = 4;

    public static final double BAUMGARTE = 0.2;
    public static final double SLOP = 0.002;
    public static final double A_MIN = 0.2;
    public static final int SWEEPS = 8;
    public static final double MAX_DS = 40.0;
    public static final double DEEP_ERROR_START = 0.3;
    public static final double DEEP_ERROR_SPAN = 0.5;
    public static final double DEEP_BIAS_SPEED = 6.0;

    public static final double FRONT_SLACK = 0.25;
    public static final double OPPOSED_DOT = -0.4;
    public static final double MAX_OFFSET = 0.3;
    public static final double MAX_TILT_SIN = 0.05;

    public static final float CLIP_LENGTH = 2.0f;

    public static double connectDistance = DEFAULT_CONNECT_DISTANCE;

    public static double mateOpenGap = DEFAULT_MATE_OPEN_GAP;
    public static double mateCloseGap = DEFAULT_MATE_CLOSE_GAP;

    public static double positionLerp = DEFAULT_POSITION_LERP;

    public static double springStiffness = DEFAULT_SPRING_STIFFNESS;
    public static double maxBiasSpeed = DEFAULT_MAX_BIAS_SPEED;

    private KnuckleTuning() {
    }

    public static float clipForGap(double hingeGap) {
        double span = mateOpenGap - mateCloseGap;
        if (span <= 1.0e-6) {
            return hingeGap <= mateCloseGap ? CLIP_LENGTH : 0.0f;
        }
        return (float) (Mth.clamp((mateOpenGap - hingeGap) / span, 0.0, 1.0) * CLIP_LENGTH);
    }
}
