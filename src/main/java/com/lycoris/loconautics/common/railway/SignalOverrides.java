package com.lycoris.loconautics.common.railway;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SignalOverrides {

    private static final Set<String> HELD = ConcurrentHashMap.newKeySet();

    private SignalOverrides() {
    }

    private static String key(UUID signal, boolean primary) {
        return signal + (primary ? "#1" : "#2");
    }

    public static void hold(UUID signal, boolean primary) {
        HELD.add(key(signal, primary));
    }

    public static void release(UUID signal, boolean primary) {
        HELD.remove(key(signal, primary));
    }

    public static boolean held(UUID signal, boolean primary) {
        return !HELD.isEmpty() && HELD.contains(key(signal, primary));
    }

    public static Set<String> all() {
        return Set.copyOf(HELD);
    }

    public static void clear() {
        HELD.clear();
    }
}
