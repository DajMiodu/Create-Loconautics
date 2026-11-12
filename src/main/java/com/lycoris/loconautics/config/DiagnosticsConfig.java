package com.lycoris.loconautics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DiagnosticsConfig {

    private DiagnosticsConfig() {
    }

    public static ModConfigSpec.BooleanValue DEBUG_OUTPUT;

    public static boolean debugOutput() {
        if (DEBUG_OUTPUT == null) {
            return false;
        }
        try {
            return DEBUG_OUTPUT.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    public enum Channel {
        FORCE("force", false),
        FORCE_LINES("forceLines", false);

        private final String key;
        private final boolean alwaysOn;
        private ModConfigSpec.BooleanValue value;

        Channel(String key, boolean alwaysOn) {
            this.key = key;
            this.alwaysOn = alwaysOn;
        }

        private void define(ModConfigSpec.Builder builder) {
            value = builder.define(key, alwaysOn);
        }

        public boolean enabled() {
            return (alwaysOn || value.get()) && debugOutput();
        }

        public boolean toggle() {
            boolean next = !value.get();
            value.set(next);
            return next;
        }
    }

    public static void define(ModConfigSpec.Builder builder) {
        DEBUG_OUTPUT = builder
                .comment("Master switch for developer output: log lines, debug chat messages, debug lines and the tick meter.",
                        "Leave off unless you are chasing a bug, it also gates the channels below.")
                .define("debugOutput", false);

        for (Channel channel : Channel.values()) {
            channel.define(builder);
        }
    }
}
