package com.lycoris.loconautics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DiagnosticsConfig {
    private static final String TRANSLATION_PREFIX = LoconauticsConfig.TRANSLATION_PREFIX + "debug.";

    private DiagnosticsConfig() {
    }

    public static ModConfigSpec.BooleanValue DISABLE_ALL;

    public enum Channel {
        FORCE("force", true),
        FORCE_LINES("forceLines", false);

        private final String key;
        private final boolean alwaysOn;
        private ModConfigSpec.BooleanValue value;

        Channel(String key, boolean alwaysOn) {
            this.key = key;
            this.alwaysOn = alwaysOn;
        }

        private void define(ModConfigSpec.Builder builder) {
            value = builder.translation(TRANSLATION_PREFIX + key).define(key, alwaysOn);
        }

        public boolean enabled() {
            return (alwaysOn || value.get()) && !DISABLE_ALL.get();
        }

        public boolean toggle() {
            boolean next = !value.get();
            value.set(next);
            return next;
        }
    }

    public static void define(ModConfigSpec.Builder builder) {
        DISABLE_ALL = builder
                .comment("Turn off every diagnostics channel below at once")
                .translation(TRANSLATION_PREFIX + "disableAll")
                .define("disableAll", false);

        for (Channel channel : Channel.values()) {
            channel.define(builder);
        }
    }
}
