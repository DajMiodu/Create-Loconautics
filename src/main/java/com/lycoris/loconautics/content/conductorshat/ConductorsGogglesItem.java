package com.lycoris.loconautics.content.conductorshat;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;

public class ConductorsGogglesItem extends GogglesItem {

    private final DyeColor color;

    public ConductorsGogglesItem(Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
    }

    public DyeColor getColor() {
        return color;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }
}
