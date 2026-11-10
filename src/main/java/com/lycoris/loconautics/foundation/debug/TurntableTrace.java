package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.LoconauticsConstants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TurntableTrace {

    private static final int MAX_LINES = 4000;
    private static final int STACK_DEPTH = 8;

    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();
    private static int lines;

    private TurntableTrace() {
    }

    public static boolean due(String key, long now, int periodTicks) {
        Long last = LAST.get(key);
        if (last != null && now - last < periodTicks) {
            return false;
        }
        LAST.put(key, now);
        return true;
    }

    public static synchronized void log(String text) {
        if (lines >= MAX_LINES) {
            return;
        }
        lines++;
        LoconauticsConstants.LOGGER.info("[turntrace] {}", text);
    }

    public static String callers() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder out = new StringBuilder();
        int taken = 0;
        for (int i = 2; i < stack.length && taken < STACK_DEPTH; i++) {
            String cls = stack[i].getClassName();
            if (cls.equals(TurntableTrace.class.getName()) || cls.startsWith("java.")) {
                continue;
            }
            out.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(stack[i].getMethodName())
                    .append(" < ");
            taken++;
        }
        return out.toString();
    }
}
