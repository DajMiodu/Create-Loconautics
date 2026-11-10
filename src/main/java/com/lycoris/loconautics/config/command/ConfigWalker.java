package com.lycoris.loconautics.config.command;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.lycoris.loconautics.LoconauticsConstants;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ConfigWalker {

    public record Entry(
            List<String> path,
            ModConfigSpec.ConfigValue<?> value,
            ModConfigSpec.ValueSpec spec
    ) {
        public String group()   { return path.getFirst(); }
        public String flatKey() { return String.join("_", path.subList(1, path.size())); }

        public String typeName() {
            ModConfigSpec.Range<?> range = spec.getRange();
            Class<?> c = range != null ? range.getClazz() : spec.getClazz();
            if (c == null || c == Object.class) return "Value";
            if (c == Boolean.class)             return "Boolean";
            if (c == Integer.class)             return "Integer";
            if (c == Long.class)                return "Long";
            if (c == Double.class)              return "Double";
            if (c == Float.class)               return "Float";
            if (c.isEnum())                     return "Enum(" + Arrays.stream(c.getEnumConstants())
                    .map(Object::toString)
                    .reduce((a, b) -> a + "|" + b).orElse("") + ")";
            return c.getSimpleName();
        }
    }

    public static Map<String, Map<String, Entry>> index(ModConfigSpec configSpec) {
        Map<String, Map<String, Entry>> result = new LinkedHashMap<>();
        walk(configSpec.getValues(), configSpec.getSpec(), new ArrayList<>(), result);
        return result;
    }

    private static void walk(UnmodifiableConfig values, UnmodifiableConfig spec,
                             List<String> path,
                             Map<String, Map<String, Entry>> out) {
        values.valueMap().forEach((key, raw) -> {
            List<String> here = new ArrayList<>(path);
            here.add(key);
            if (raw instanceof ModConfigSpec.ConfigValue<?> cv) {
                Object specRaw = spec.valueMap().get(key);
                if (specRaw instanceof ModConfigSpec.ValueSpec vs) {
                    Entry entry = new Entry(here, cv, vs);
                    out.computeIfAbsent(entry.group(), k -> new LinkedHashMap<>())
                            .put(entry.flatKey(), entry);
                }
            } else if (raw instanceof UnmodifiableConfig nested) {
                Object specRaw = spec.valueMap().get(key);
                if (specRaw instanceof UnmodifiableConfig nestedSpec) {
                    walk(nested, nestedSpec, here, out);
                }
            }
        });
    }

    public static <T> T getOrDefault(Supplier<T> getter, T fallback, String key) {
        try {
            return getter.get();
        } catch (Exception e) {
            LoconauticsConstants.LOGGER.warn("Config read failed for {}, using fallback {}", key, fallback, e);
            return fallback;
        }
    }

    private ConfigWalker() {}
}