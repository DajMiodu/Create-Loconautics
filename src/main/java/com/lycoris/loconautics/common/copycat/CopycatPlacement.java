package com.lycoris.loconautics.common.copycat;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public enum CopycatPlacement implements StringRepresentable {

    TOP("top", 0),
    CENTER("center", -2),
    BOTTOM("bottom", -4);

    public static final EnumProperty<CopycatPlacement> PLACEMENT =
            EnumProperty.create("placement", CopycatPlacement.class);

    private final String name;
    private final int pixels;

    CopycatPlacement(String name, int pixels) {
        this.name = name;
        this.pixels = pixels;
    }

    public int pixels() {
        return pixels;
    }

    public float blocks() {
        return pixels / 16.0f;
    }

    public static CopycatPlacement aimedAt(BlockPlaceContext context) {
        double height = context.getClickLocation().y - context.getClickedPos().getY();
        if (height > 2.0 / 3.0) {
            return TOP;
        }
        return height > 1.0 / 3.0 ? CENTER : BOTTOM;
    }

    public CopycatPlacement next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
