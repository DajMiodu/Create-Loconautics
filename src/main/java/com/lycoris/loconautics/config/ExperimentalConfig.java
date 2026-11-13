package com.lycoris.loconautics.config;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.experimental.ExperimentalFeature;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class ExperimentalConfig {

    private static final Map<ExperimentalFeature, ModConfigSpec.BooleanValue> VALUES = new EnumMap<>(ExperimentalFeature.class);

    private static final String PREFIX = LoconauticsConstants.MOD_ID + ".configuration.experimental";

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Features that are not ready. Off, a feature is hidden from the creative tab, cannot be placed or used, and its recipes do not load.",
                "Changes need a restart.").translation(PREFIX).push("experimental");
        for (ExperimentalFeature feature : ExperimentalFeature.values()) {
            VALUES.put(feature, builder.translation(PREFIX + "." + feature.key()).define(feature.key(), false));
        }
        builder.pop();
        SPEC = builder.build();
    }

    public static boolean enabled(ExperimentalFeature feature) {
        if (!FMLLoader.isProduction()) {
            return true;
        }
        return SPEC.isLoaded() && VALUES.get(feature).get();
    }

    private ExperimentalConfig() {
    }
}
