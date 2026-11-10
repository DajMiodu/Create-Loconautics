package com.lycoris.loconautics.common.creative;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.combustion.SmallEngineBlocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class CreativeSections {

    public static final int ROW_SIZE = 9;

    private static final List<CreativeSection> ALL = new ArrayList<>();

    public static final CreativeSection BOILERWORKS = add("boilerworks", () -> BoilerBlocks.FIREBOX_ITEM.get());
    public static final CreativeSection DIESELWORKS = add("dieselworks", () -> SmallEngineBlocks.ENGINE_ITEMS.get(SmallEngineBlocks.TAB_COLOR).get());

    private CreativeSections() {
    }

    private static CreativeSection add(String id, Supplier<? extends Item> first) {
        CreativeSection section = new CreativeSection(id, first);
        ALL.add(section);
        return section;
    }

    public static List<CreativeSection> all() {
        return ALL;
    }

    public static List<ItemStack> layout(List<ItemStack> items) {
        for (CreativeSection section : ALL) {
            section.place(-1);
        }
        List<ItemStack> laid = new ArrayList<>(items.size() + ROW_SIZE * (ALL.size() + 1));
        pad(laid, ROW_SIZE);
        for (ItemStack stack : items) {
            for (CreativeSection section : ALL) {
                if (section.row() < 0 && stack.is(section.first())) {
                    pad(laid, (ROW_SIZE - laid.size() % ROW_SIZE) % ROW_SIZE);
                    section.place(laid.size() / ROW_SIZE);
                    pad(laid, ROW_SIZE);
                }
            }
            laid.add(stack);
        }
        return laid;
    }

    private static void pad(List<ItemStack> list, int count) {
        for (int i = 0; i < count; i++) {
            list.add(ItemStack.EMPTY);
        }
    }
}
