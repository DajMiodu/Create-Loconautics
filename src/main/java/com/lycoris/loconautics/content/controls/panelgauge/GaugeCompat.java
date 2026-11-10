package com.lycoris.loconautics.content.controls.panelgauge;

import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GaugeCompat {

    public record Entry(String modId, String name, List<String> blocks) {

        public boolean installed() {
            return ModList.get().isLoaded(modId);
        }
    }

    private GaugeCompat() {
    }

    public static List<Entry> sorted() {
        Map<String, Entry> byMod = new LinkedHashMap<>();
        for (GaugeMetric.Group group : GaugeMetric.Group.values()) {
            byMod.computeIfAbsent(group.modId(), id -> new Entry(id, group.modName(), new ArrayList<>()))
                    .blocks().add(group.blockName());
        }
        List<Entry> list = new ArrayList<>(byMod.values());
        list.sort(Comparator.comparing(entry -> !entry.installed()));
        return list;
    }
}
