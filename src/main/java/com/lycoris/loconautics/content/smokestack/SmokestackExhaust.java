package com.lycoris.loconautics.content.smokestack;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.AllSoundEvents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SmokestackExhaust {

    private static final double DRIVER_DIAMETER = 25.0 / 16.0;
    private static final double BEATS_PER_BLOCK = 4.0 / (Math.PI * DRIVER_DIAMETER);
    private static final int MIN_BEAT_TICKS = 2;
    private static final double MOVING_SPEED = 0.01;
    private static final double FULL_SPEED = 0.6;
    private static final float CHUFF_VOLUME = 0.2f;
    private static final float IDLE_CHUFF_VOLUME = 0.25f;
    private static final float RELEASE_VOLUME = 0.4f;
    private static final float MAX_STEAM_SPENT = 10.0f;

    private double phase;
    private int beat;
    private int ticksSinceBeat;
    private float steamSpent;

    public static double wheelSpeed(BlockPos pos) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(pos);
        if (sub == null) {
            return 0.0;
        }
        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
        if (marker == null || marker.derailed() || marker.brakeHard()) {
            return 0.0;
        }
        double speed = Math.abs(marker.speed());
        for (float driven : marker.drivenBogeys().values()) {
            speed = Math.max(speed, Math.abs(driven));
        }
        return speed;
    }

    public static float pace(double wheelSpeed) {
        return (float) Mth.clamp(wheelSpeed / FULL_SPEED, 0.0, 1.0);
    }

    public static boolean isMoving(double wheelSpeed) {
        return wheelSpeed >= MOVING_SPEED;
    }

    public boolean tick(Level level, Vec3 at, double wheelSpeed, int firePower) {
        ticksSinceBeat++;
        if (!isMoving(wheelSpeed)) {
            release(level, at);
            phase = 0.0;
            return false;
        }
        steamSpent = Math.min(steamSpent + (float) Math.min(0.5, wheelSpeed / 10.0), MAX_STEAM_SPENT);
        phase = Math.min(phase + wheelSpeed * BEATS_PER_BLOCK, 1.0);
        if (phase < 1.0 || ticksSinceBeat < MIN_BEAT_TICKS) {
            return false;
        }
        phase -= 1.0;
        ticksSinceBeat = 0;
        chuff(level, at, wheelSpeed, firePower);
        return true;
    }

    public void idleChuff(Level level, Vec3 at, int firePower) {
        float fire = 0.35f + 0.65f * firePower / 15.0f;
        float pitch = 0.95f + (level.random.nextFloat() - 0.5f) * 0.1f;
        AllSoundEvents.STEAM.playAt(level, at, IDLE_CHUFF_VOLUME * fire, pitch, false);
    }

    public void reset() {
        phase = 0.0;
        steamSpent = 0.0f;
    }

    private void chuff(Level level, Vec3 at, double wheelSpeed, int firePower) {
        float pace = pace(wheelSpeed);
        float fire = 0.35f + 0.65f * firePower / 15.0f;
        boolean downbeat = beat % 4 == 0;
        float accent = downbeat ? 1.5f : beat % 2 == 0 ? 0.75f : 0.45f;
        float volume = CHUFF_VOLUME * accent * fire * (0.6f + 0.4f * pace);
        float pitch = (downbeat ? 0.8f : 1.17f) * (0.9f + 0.2f * pace)
                + (level.random.nextFloat() - 0.5f) * 0.06f;
        AllSoundEvents.STEAM.playAt(level, at, volume, pitch, false);
        beat++;
    }

    private void release(Level level, Vec3 at) {
        if (steamSpent > 1.0f) {
            float volume = steamSpent / MAX_STEAM_SPENT * RELEASE_VOLUME;
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL,
                    0.25f * volume, 0.78f, false);
            AllSoundEvents.STEAM.playAt(level, at, 0.75f * volume, 0.5f, false);
        }
        steamSpent = 0.0f;
    }
}
