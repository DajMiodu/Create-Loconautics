package com.lycoris.loconautics.content.cerisene;

public final class CeriseneTuning {

    private CeriseneTuning() {}

    public static final int LIGHT_LEVEL = 7;

    public static final int MAX_HEAT = 4;

    public static final int ENGINE_MAX_HEAT = MAX_HEAT - 1;

    public static final int HEAT_RANGE = 2;

    public static final int HEAT_INTERVAL_TICKS = 20;

    public static final float EXPLOSION_CHANCE = 0.25f;

    public static final int CHAIN_DELAY_TICKS = 2;

    public static final int MAX_EXPLOSIONS_PER_TICK = 8;

    public static final int EFFECT_RADIUS = 2;

    public static final int IGNITE_SECONDS = 3;

    public static final double KNOCKBACK = 0.35;

    public static final int FLAMMABILITY = 5;

    public static final int FIRE_SPREAD_SPEED = 5;

    public static final int WATER_PER_BUCKET = 250;

    public static final int PROCESSING_TIME = 200;

    public static final int FLUID_PER_BUCKET = 1000;

    public static final int RAW_PER_BLOCK = 4;

    // tint multiplies the pink texture, so cold is white and hot is red
    public static final int PINK_TINT = 0xFFFFFF;

    public static final int RED_TINT = 0xFF3A3A;

    public static final int FLUID_TINT = 0xC8FFFFFF;
}
