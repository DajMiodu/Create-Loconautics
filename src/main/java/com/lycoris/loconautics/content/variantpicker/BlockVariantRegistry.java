package com.lycoris.loconautics.content.variantpicker;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.variantpicker.data.LoadedVariantFamily;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockVariantRegistry {

    public static final int COLOR_COUNT = 17;
    public static final int COLORLESS_INDEX = 16;
    public static final int MAX_TYPES = 16;

    public record VariantType(String translationKey, Block[] colorVariants, Block[][] wrapped, int materialCount) {

        public Block at(int colorIndex) {
            if (colorIndex < 0 || colorIndex >= colorVariants.length) return null;
            return colorVariants[colorIndex];
        }

        public boolean hasWrappings() {
            return materialCount > 0 && wrapped != null;
        }

        public Block wrappedAt(int colorIndex, int materialIndex) {
            if (!hasWrappings()) return null;
            if (colorIndex < 0 || colorIndex >= wrapped.length) return null;
            Block[] row = wrapped[colorIndex];
            if (row == null || materialIndex < 0 || materialIndex >= row.length) return null;
            return row[materialIndex];
        }
    }

    public record Location(int family, int type, int color, int material) {
    }

    private static final List<List<VariantType>> FAMILIES = new ArrayList<>();
    private static final Map<Block, Location> INDEX = new HashMap<>();

    private BlockVariantRegistry() {
    }

    public static synchronized void rebuild(List<LoadedVariantFamily> datapack) {
        FAMILIES.clear();
        INDEX.clear();
        for (LoadedVariantFamily fam : datapack) {
            registerLoaded(fam);
        }
    }

    private static void registerLoaded(LoadedVariantFamily fam) {
        List<VariantType> types = new ArrayList<>();
        for (LoadedVariantFamily.Type lt : fam.types()) {
            Block[] colors = new Block[COLOR_COUNT];
            boolean anyColor = false;
            for (var e : lt.colors().entrySet()) {
                Block b = BuiltInRegistries.BLOCK.getOptional(e.getValue()).orElse(null);
                if (b == null) {
                    LoconauticsConstants.LOGGER.warn("[VariantPicker] block {} is not registered, skipped", e.getValue());
                    continue;
                }
                if (e.getKey() >= 0 && e.getKey() < COLOR_COUNT) {
                    colors[e.getKey()] = b;
                    anyColor = true;
                }
            }
            if (!anyColor) continue;
            int matCount = lt.materials().size();
            Block[][] wrapped = matCount > 0 ? new Block[COLOR_COUNT][matCount] : null;
            if (wrapped != null) {
                for (var ce : lt.wrapped().entrySet()) {
                    if (ce.getKey() < 0 || ce.getKey() >= COLOR_COUNT) continue;
                    for (var me : ce.getValue().entrySet()) {
                        Block b = BuiltInRegistries.BLOCK.getOptional(me.getValue()).orElse(null);
                        if (b != null && me.getKey() >= 0 && me.getKey() < matCount) {
                            wrapped[ce.getKey()][me.getKey()] = b;
                        }
                    }
                }
            }
            types.add(new VariantType(lt.name(), colors, wrapped, matCount));
        }
        for (int i = 0; i < types.size(); i += MAX_TYPES) {
            registerFamily(types.subList(i, Math.min(i + MAX_TYPES, types.size())));
        }
    }

    private static void registerFamily(List<VariantType> types) {
        if (types.isEmpty()) return;
        int familyIdx = FAMILIES.size();
        FAMILIES.add(List.copyOf(types));
        for (int t = 0; t < types.size(); t++) {
            VariantType type = types.get(t);
            Block[] colors = type.colorVariants();
            for (int c = 0; c < colors.length; c++) {
                if (colors[c] != null) {
                    INDEX.putIfAbsent(colors[c], new Location(familyIdx, t, c, -1));
                }
                for (int m = 0; m < type.materialCount(); m++) {
                    Block w = type.wrappedAt(c, m);
                    if (w != null) {
                        INDEX.putIfAbsent(w, new Location(familyIdx, t, c, m));
                    }
                }
            }
        }
    }

    public static Location locate(Block block) {
        return INDEX.get(block);
    }

    public static List<VariantType> family(int index) {
        return FAMILIES.get(index);
    }

    public static boolean isKnownVariant(Block block) {
        return INDEX.containsKey(block);
    }
}
