package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.portal.PortalTrace;

import java.util.StringJoiner;

public final class StallWatchdog {

    private static final long POLL_MS = 100;
    private static final long FIRST_DUMP_MS = 400;
    private static final long SECOND_DUMP_MS = 1500;
    private static final int FRAMES = 30;

    private static volatile long lastTickNanos;
    private static volatile long tickCount;
    private static volatile Thread serverThread;
    private static Thread watcher;

    private StallWatchdog() {
    }

    public static void onServerTick() {
        serverThread = Thread.currentThread();
        lastTickNanos = System.nanoTime();
        tickCount++;
        if (watcher == null && PortalTrace.on()) {
            watcher = new Thread(StallWatchdog::watch, "loconautics stall watchdog");
            watcher.setDaemon(true);
            watcher.start();
        }
    }

    private static void watch() {
        long dumpedTick = -1;
        int dumps = 0;
        while (true) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException interrupted) {
                return;
            }
            Thread target = serverThread;
            if (target == null || !PortalTrace.on()) {
                continue;
            }
            long tick = tickCount;
            if (tick != dumpedTick) {
                dumpedTick = tick;
                dumps = 0;
            }
            long stallMs = (System.nanoTime() - lastTickNanos) / 1_000_000L;
            boolean due = dumps == 0 ? stallMs >= FIRST_DUMP_MS : dumps == 1 && stallMs >= SECOND_DUMP_MS;
            if (!due) {
                continue;
            }
            dumps++;
            StringJoiner trace = new StringJoiner("\n    at ", "\n    at ", "");
            StackTraceElement[] frames = target.getStackTrace();
            for (int i = 0; i < Math.min(FRAMES, frames.length); i++) {
                trace.add(frames[i].toString());
            }
            LoconauticsConstants.LOGGER.warn("[stall] server thread stuck {}ms in tick {}, stack:{}", stallMs, tick, trace);
        }
    }
}
