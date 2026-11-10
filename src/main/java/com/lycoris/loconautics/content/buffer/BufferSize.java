package com.lycoris.loconautics.content.buffer;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public enum BufferSize implements StringRepresentable {

    HALF("half", 11),
    FULL("full", 16),
    ONE_AND_HALF("one_and_half", 24);

    public static final EnumProperty<BufferSize> SIZE = EnumProperty.create("size", BufferSize.class);

    public static final int RESTING_REACH_PIXELS = 8;

    private final String name;
    private final int reachPixels;

    BufferSize(String name, int reachPixels) {
        this.name = name;
        this.reachPixels = reachPixels;
    }

    public int extensionPixels() {
        return reachPixels - RESTING_REACH_PIXELS;
    }

    public int sleeveOutPixels() {
        return this == HALF ? 0 : 3;
    }

    public double extensionBlocks() {
        return extensionPixels() / 16.0;
    }

    public BufferSize next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
