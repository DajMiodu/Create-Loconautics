package com.lycoris.loconautics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DiagnosticsConfig {

    private DiagnosticsConfig() {
    }

    public static boolean debugOutput() {
        return LoconauticsConfig.DEBUG;
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
        for (Channel channel : Channel.values()) {
            channel.define(builder);
        }
    }
}
