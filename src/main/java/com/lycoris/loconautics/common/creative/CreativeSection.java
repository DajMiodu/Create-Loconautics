package com.lycoris.loconautics.common.creative;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class CreativeSection {

    private final String id;
    private final Supplier<? extends Item> first;
    private int row = -1;

    CreativeSection(String id, Supplier<? extends Item> first) {
        this.id = id;
        this.first = first;
    }

    public Component title() {
        return Component.translatable("creative_section.loconautics." + id);
    }

    public ResourceLocation sprite() {
        return LoconauticsConstants.id("section_banner_" + id);
    }

    public int row() {
        return row;
    }

    Item first() {
        return first.get();
    }

    void place(int row) {
        this.row = row;
    }
}
