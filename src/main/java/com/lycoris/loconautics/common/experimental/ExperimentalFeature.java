package com.lycoris.loconautics.common.experimental;

import com.lycoris.loconautics.config.ExperimentalConfig;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.cerisene.CeriseneBlocks;
import com.lycoris.loconautics.content.combustion.SmallEngineBlocks;
import com.lycoris.loconautics.content.industrial.IndustrialBlocks;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public enum ExperimentalFeature {
    BOILERWORKS("boilerworks", () -> List.of(
            BoilerBlocks.FIREBOX_ITEM.get(),
            BoilerBlocks.BOILER_BODY_ITEM.get(),
            BoilerBlocks.STEAM_TAKEOFF_ITEM.get(),
            BoilerBlocks.SMART_SAFETY_VALVE_ITEM.get(),
            BoilerBlocks.FIREBOX_STOCKER_ITEM.get(),
            LoconauticsRegistries.SMOKESTACK_ITEM.get(),
            LoconauticsRegistries.BOILER_TUBES.get(),
            LoconauticsRegistries.VALVE_GEAR.get())),
    DIESELWORKS("dieselworks", () -> new ArrayList<>(SmallEngineBlocks.ENGINE_ITEMS.values().stream().map(DeferredHolder::get).toList())),
    INDUSTRIAL_FLUIDS("industrialFluids", () -> List.of(
            LoconauticsRegistries.INDUSTRIAL_IRON_SHEET.get(),
            LoconauticsRegistries.INDUSTRIAL_IRON_ROD.get(),
            IndustrialBlocks.PIPE_ITEM.get(),
            IndustrialBlocks.SMART_PIPE_ITEM.get(),
            IndustrialBlocks.PUMP_ITEM.get(),
            IndustrialBlocks.VALVE_ITEM.get(),
            IndustrialBlocks.TANK_ITEM.get(),
            CeriseneBlocks.RAW_CERISENE.get(),
            CeriseneBlocks.CERISENE_BLOCK_ITEM.get(),
            CeriseneBlocks.CERISENE_BUCKET.get()));

    private static Map<Item, ExperimentalFeature> byItem;

    private final String key;
    private final Supplier<List<? extends Item>> items;

    ExperimentalFeature(String key, Supplier<List<? extends Item>> items) {
        this.key = key;
        this.items = items;
    }

    public String key() {
        return key;
    }

    public boolean enabled() {
        return ExperimentalConfig.enabled(this);
    }

    public static ExperimentalFeature byKey(String key) {
        for (ExperimentalFeature feature : values()) {
            if (feature.key.equals(key)) {
                return feature;
            }
        }
        return null;
    }

    public static boolean disabled(Item item) {
        if (byItem == null) {
            Map<Item, ExperimentalFeature> map = new HashMap<>();
            for (ExperimentalFeature feature : values()) {
                for (Item gated : new ArrayList<>(feature.items.get())) {
                    map.put(gated, feature);
                }
            }
            byItem = map;
        }
        ExperimentalFeature feature = byItem.get(item);
        return feature != null && !feature.enabled();
    }
}
