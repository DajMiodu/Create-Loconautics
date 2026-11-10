package com.lycoris.loconautics.content.variantpicker.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.variantpicker.BlockVariantRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VariantFamilyLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "variant_families";

    private static volatile List<LoadedVariantFamily> loaded = List.of();

    public VariantFamilyLoader() {
        super(GSON, DIRECTORY);
    }

    public static List<LoadedVariantFamily> loaded() {
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        List<LoadedVariantFamily> parsed = new ArrayList<>();
        for (var e : files.entrySet()) {
            try {
                parsed.add(LoadedVariantFamily.fromJson(GSON.fromJson(e.getValue(), JsonObject.class)));
            } catch (RuntimeException ex) {
                LoconauticsConstants.LOGGER.error("[VariantPicker] bad family {}", e.getKey(), ex);
            }
        }
        loaded = List.copyOf(parsed);
        BlockVariantRegistry.rebuild(loaded);
        LoconauticsConstants.LOGGER.info("[VariantPicker] loaded {} datapack families", loaded.size());
    }
}
