package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class TickMeter {

    public static final double YELLOW_GAP_MS = 55.0;
    public static final double RED_GAP_MS = 100.0;

    private static final int WINDOW = 20;
    private static final double NANOS_PER_MS = 1_000_000.0;
    private static final long RESTART_GAP_NANOS = 2_000_000_000L;
    private static final int LOG_COOLDOWN_TICKS = 10;
    private static final long SAMPLE_AFTER_NANOS = 40_000_000L;
    private static final long SAMPLE_EVERY_MS = 5L;
    private static final int SAMPLE_FRAMES = 7;
    private static final int TOP_STACKS = 3;
    private static final long[] GAPS = new long[WINDOW];
    private static final Map<String, Integer> SAMPLES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> LEVEL_START = new LinkedHashMap<>();
    private static final Map<ResourceKey<Level>, Double> LEVEL_MS = new LinkedHashMap<>();

    private static volatile double tps = 20.0;
    private static volatile double gapMs = 50.0;
    private static volatile double workMs;
    private static long lastStart;
    private static long tickStart;
    private static int index;
    private static int filled;
    private static int sinceLog = LOG_COOLDOWN_TICKS;
    private static long lastGcMs;
    private static volatile Thread serverThread;
    private static volatile long lastStartNanos;
    private static Thread sampler;

    private TickMeter() {
    }

    public static double tps() {
        return tps;
    }

    public static double gapMs() {
        return gapMs;
    }

    public static double workMs() {
        return workMs;
    }

    @SubscribeEvent
    public static void onTickPre(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
        serverThread = Thread.currentThread();
        lastStartNanos = tickStart;
        if (sampler == null) {
            sampler = new Thread(TickMeter::sample, "loconautics tick sampler");
            sampler.setDaemon(true);
            sampler.start();
        }
        long gap = tickStart - lastStart;
        lastStart = tickStart;
        if (gap > RESTART_GAP_NANOS) {
            index = 0;
            filled = 0;
            lastGcMs = totalGcMs();
            return;
        }
        gapMs = gap / NANOS_PER_MS;
        GAPS[index] = gap;
        index = (index + 1) % WINDOW;
        filled = Math.min(filled + 1, WINDOW);
        long total = 0L;
        for (int i = 0; i < filled; i++) {
            total += GAPS[i];
        }
        tps = Math.min(20.0, filled * 1_000_000_000.0 / total);
    }

    @SubscribeEvent
    public static void onLevelPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEVEL_START.put(level.dimension(), System.nanoTime());
        }
    }

    @SubscribeEvent
    public static void onLevelPost(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Long start = LEVEL_START.get(level.dimension());
            if (start != null) {
                LEVEL_MS.put(level.dimension(), (System.nanoTime() - start) / NANOS_PER_MS);
            }
        }
    }

    @SubscribeEvent
    public static void onTickPost(ServerTickEvent.Post event) {
        workMs = (System.nanoTime() - tickStart) / NANOS_PER_MS;
        sinceLog++;
        if (gapMs < YELLOW_GAP_MS || filled == 0 || sinceLog < LOG_COOLDOWN_TICKS) {
            if (gapMs < YELLOW_GAP_MS) {
                SAMPLES.clear();
            }
            return;
        }
        sinceLog = 0;
        long gc = totalGcMs();
        StringBuilder levels = new StringBuilder();
        for (Map.Entry<ResourceKey<Level>, Double> entry : LEVEL_MS.entrySet()) {
            levels.append(' ').append(entry.getKey().location()).append('=')
                    .append(String.format("%.1f", entry.getValue())).append("ms");
        }
        LoconauticsConstants.LOGGER.info("[tickmeter] {} gap {} ms, tick work {} ms, gc since last log {} ms, tps {},{}",
                gapMs < RED_GAP_MS ? "YELLOW" : "RED", String.format("%.1f", gapMs), String.format("%.1f", workMs),
                gc - lastGcMs, String.format("%.1f", tps), levels);
        lastGcMs = gc;
        List<Map.Entry<String, Integer>> top = new ArrayList<>(SAMPLES.entrySet());
        top.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        SAMPLES.clear();
        for (int i = 0; i < Math.min(TOP_STACKS, top.size()); i++) {
            LoconauticsConstants.LOGGER.info("[tickmeter]   cause {} samples at {}", top.get(i).getValue(), top.get(i).getKey());
        }
    }

    // only samples once a tick has run past 40 ms, so a normal tick costs nothing
    private static void sample() {
        while (true) {
            try {
                Thread.sleep(SAMPLE_EVERY_MS);
            } catch (InterruptedException interrupted) {
                return;
            }
            Thread target = serverThread;
            if (target == null || System.nanoTime() - lastStartNanos < SAMPLE_AFTER_NANOS) {
                continue;
            }
            StackTraceElement[] frames = target.getStackTrace();
            if (frames.length == 0) {
                continue;
            }
            StringBuilder key = new StringBuilder();
            for (int i = 0; i < Math.min(SAMPLE_FRAMES, frames.length); i++) {
                if (i > 0) {
                    key.append(" < ");
                }
                key.append(shorten(frames[i]));
            }
            SAMPLES.merge(key.toString(), 1, Integer::sum);
        }
    }

    private static String shorten(StackTraceElement frame) {
        String cls = frame.getClassName();
        return cls.substring(cls.lastIndexOf('.') + 1) + "." + frame.getMethodName();
    }

    private static long totalGcMs() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, bean.getCollectionTime());
        }
        return total;
    }
}
