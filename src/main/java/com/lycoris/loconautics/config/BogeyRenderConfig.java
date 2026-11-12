package com.lycoris.loconautics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BogeyRenderConfig {

    public enum Style {
        CLASSIC,
        ROLLING
    }

    private static final Style DEFAULT_STYLE = Style.CLASSIC;

    private static ModConfigSpec.EnumValue<Style> STYLE;

    private BogeyRenderConfig() {
    }

    public static void define(ModConfigSpec.Builder builder) {
        builder.push("bogeys");

        STYLE = builder
                .comment("Classic bogeys stay level with the rail they sit on.",
                        "Rolling bogeys keep the roll of the car instead.")
                .defineEnum("renderStyle", DEFAULT_STYLE);

        builder.pop();
    }

    public static boolean rolling() {
        if (STYLE == null) {
            return DEFAULT_STYLE == Style.ROLLING;
        }
        try {
            return STYLE.get() == Style.ROLLING;
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_STYLE == Style.ROLLING;
        }
    }
}
