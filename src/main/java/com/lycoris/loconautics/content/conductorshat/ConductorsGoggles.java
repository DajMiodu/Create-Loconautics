package com.lycoris.loconautics.content.conductorshat;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ConductorsGoggles {

    private ConductorsGoggles() {}

    public static final Map<DyeColor, DeferredHolder<Item, ConductorsGogglesItem>> GOGGLES = registerGoggles();

    public static String gogglesName(DyeColor color) {
        return color.getSerializedName() + "_conductors_goggles";
    }

    public static void init() {}

    public static void registerWearing() {
        GogglesItem.addIsWearingPredicate(player ->
                player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ConductorsGogglesItem);
        if (ModList.get().isLoaded("curios")) {
            GogglesItem.addIsWearingPredicate(CuriosConductorsGoggles::isWorn);
        }
    }

    private static Map<DyeColor, DeferredHolder<Item, ConductorsGogglesItem>> registerGoggles() {
        Map<DyeColor, DeferredHolder<Item, ConductorsGogglesItem>> goggles = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            goggles.put(color, LoconauticsRegistries.ITEMS.register(gogglesName(color), () ->
                    new ConductorsGogglesItem(new Item.Properties().stacksTo(1), color)));
        }
        return Collections.unmodifiableMap(goggles);
    }
}
