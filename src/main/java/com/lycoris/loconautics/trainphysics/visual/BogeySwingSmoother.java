package com.lycoris.loconautics.trainphysics.visual;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class BogeySwingSmoother {

    private static final class Sample {
        private final Quaternionf prev = new Quaternionf();
        private final Quaternionf curr = new Quaternionf();
        private long tick = Long.MIN_VALUE;
    }

    private static final Map<AbstractBogeyBlockEntity, Sample> SAMPLES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BogeySwingSmoother() {
    }

    public static Quaternionf interp(AbstractBogeyBlockEntity be, Quaternionf synced, float partialTick,
                                     Quaternionf dest) {
        long now = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        Sample sample = SAMPLES.computeIfAbsent(be, key -> new Sample());
        if (sample.tick == Long.MIN_VALUE) {
            sample.prev.set(synced);
            sample.curr.set(synced);
            sample.tick = now;
        } else if (sample.tick != now) {
            // latched on the first frame of the tick, a packet landing mid tick must not overwrite the live sample
            sample.prev.set(sample.curr);
            sample.curr.set(synced);
            sample.tick = now;
        }
        return sample.prev.slerp(sample.curr, Mth.clamp(partialTick, 0.0f, 1.0f), dest).normalize();
    }
}
