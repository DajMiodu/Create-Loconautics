package com.lycoris.loconautics.content.variantpicker.data;

import com.google.gson.JsonObject;
import com.lycoris.loconautics.content.variantpicker.BlockVariantRegistry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LoadedVariantFamily(List<Type> types) {

    public record Type(String name, Map<Integer, ResourceLocation> colors, List<String> materials,
                       Map<Integer, Map<Integer, ResourceLocation>> wrapped) {
    }

    public static int colorIndex(String name) {
        if (name.equals("colorless")) return BlockVariantRegistry.COLORLESS_INDEX;
        DyeColor dye = DyeColor.byName(name, null);
        return dye == null ? -1 : dye.getId();
    }


    public static LoadedVariantFamily fromJson(JsonObject root) {
        List<Type> types = new ArrayList<>();
        if (root.has("blocks")) {
            for (var el : root.getAsJsonArray("blocks")) {
                ResourceLocation id = ResourceLocation.parse(el.getAsString());
                Map<Integer, ResourceLocation> colors = new LinkedHashMap<>();
                colors.put(BlockVariantRegistry.COLORLESS_INDEX, id);
                types.add(new Type(id.toString(), colors, List.of(), Map.of()));
            }
        }
        if (!root.has("types")) return new LoadedVariantFamily(types);
        for (var el : root.getAsJsonArray("types")) {
            JsonObject t = el.getAsJsonObject();
            String name = t.get("name").getAsString();

            Map<Integer, ResourceLocation> colors = new LinkedHashMap<>();
            if (t.has("block")) {
                colors.put(BlockVariantRegistry.COLORLESS_INDEX, ResourceLocation.parse(t.get("block").getAsString()));
            }
            if (t.has("colors")) {
                for (var e : t.getAsJsonObject("colors").entrySet()) {
                    int ci = colorIndex(e.getKey());
                    if (ci >= 0) colors.put(ci, ResourceLocation.parse(e.getValue().getAsString()));
                }
            }

            List<String> materials = new ArrayList<>();
            if (t.has("materials")) {
                for (var m : t.getAsJsonArray("materials")) materials.add(m.getAsString());
            }

            Map<Integer, Map<Integer, ResourceLocation>> wrapped = new LinkedHashMap<>();
            if (t.has("wrapped") && !materials.isEmpty()) {
                for (var ce : t.getAsJsonObject("wrapped").entrySet()) {
                    int ci = colorIndex(ce.getKey());
                    if (ci < 0) continue;
                    Map<Integer, ResourceLocation> row = new LinkedHashMap<>();
                    for (var me : ce.getValue().getAsJsonObject().entrySet()) {
                        int mi = materials.indexOf(me.getKey());
                        if (mi >= 0) row.put(mi, ResourceLocation.parse(me.getValue().getAsString()));
                    }
                    if (!row.isEmpty()) wrapped.put(ci, row);
                }
            }
            types.add(new Type(name, colors, materials, wrapped));
        }
        return new LoadedVariantFamily(types);
    }


    public static void write(FriendlyByteBuf buf, List<LoadedVariantFamily> families) {
        buf.writeVarInt(families.size());
        for (LoadedVariantFamily fam : families) {
            buf.writeVarInt(fam.types.size());
            for (Type type : fam.types) {
                buf.writeUtf(type.name);
                buf.writeVarInt(type.colors.size());
                for (var e : type.colors.entrySet()) {
                    buf.writeVarInt(e.getKey());
                    buf.writeResourceLocation(e.getValue());
                }
                buf.writeVarInt(type.materials.size());
                for (String m : type.materials) buf.writeUtf(m);
                buf.writeVarInt(type.wrapped.size());
                for (var ce : type.wrapped.entrySet()) {
                    buf.writeVarInt(ce.getKey());
                    buf.writeVarInt(ce.getValue().size());
                    for (var me : ce.getValue().entrySet()) {
                        buf.writeVarInt(me.getKey());
                        buf.writeResourceLocation(me.getValue());
                    }
                }
            }
        }
    }

    public static List<LoadedVariantFamily> read(FriendlyByteBuf buf) {
        int fc = buf.readVarInt();
        List<LoadedVariantFamily> families = new ArrayList<>(fc);
        for (int f = 0; f < fc; f++) {
            int tc = buf.readVarInt();
            List<Type> types = new ArrayList<>(tc);
            for (int t = 0; t < tc; t++) {
                String name = buf.readUtf();
                int cc = buf.readVarInt();
                Map<Integer, ResourceLocation> colors = new LinkedHashMap<>();
                for (int c = 0; c < cc; c++) colors.put(buf.readVarInt(), buf.readResourceLocation());
                int mc = buf.readVarInt();
                List<String> materials = new ArrayList<>(mc);
                for (int m = 0; m < mc; m++) materials.add(buf.readUtf());
                int wc = buf.readVarInt();
                Map<Integer, Map<Integer, ResourceLocation>> wrapped = new LinkedHashMap<>();
                for (int w = 0; w < wc; w++) {
                    int ci = buf.readVarInt();
                    int rc = buf.readVarInt();
                    Map<Integer, ResourceLocation> row = new LinkedHashMap<>();
                    for (int r = 0; r < rc; r++) row.put(buf.readVarInt(), buf.readResourceLocation());
                    wrapped.put(ci, row);
                }
                types.add(new Type(name, colors, materials, wrapped));
            }
            families.add(new LoadedVariantFamily(types));
        }
        return families;
    }
}
