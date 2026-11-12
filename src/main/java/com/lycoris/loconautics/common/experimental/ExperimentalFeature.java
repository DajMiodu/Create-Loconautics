package com.lycoris.loconautics.common.experimental;

import com.lycoris.loconautics.config.ExperimentalConfig;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public enum ExperimentalFeature {
    ;

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
