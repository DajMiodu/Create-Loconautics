package com.lycoris.loconautics.content.buffer;

public final class BufferTuning {

    private BufferTuning() {
    }

    public static final double SOFT_FREQUENCY = 14.0;
    public static final double HARD_FREQUENCY = 40.0;
    public static final double DAMPING_RATIO = 1.2;
    public static final double SOFT_STROKE = 0.5;
    public static final double MAX_REBOUND = 0.3;

    public static final double WATCH_REACH = 1.5;
    public static final double MAX_PENETRATION = 1.0;
    public static final double MAX_LATERAL = 0.6;
    public static final double OPPOSED_DOT = -0.7;
    public static final int SCAN_INTERVAL = 2;
    public static final int CONTACT_MISSES = 5;
    public static final double ATTACK = 0.5;
    public static final double RELEASE = 0.12;

    public static final double HEAD_HEIGHT = 10.0 / 16.0;
    public static final double COMPRESSION_STEP = 0.25 / 16.0;
    public static final float DISPLAY_LERP = 0.35f;
}
