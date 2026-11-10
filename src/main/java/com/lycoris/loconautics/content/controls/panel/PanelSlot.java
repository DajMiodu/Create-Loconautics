package com.lycoris.loconautics.content.controls.panel;

import com.mojang.serialization.Codec;
import net.createmod.catnip.lang.Lang;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public enum PanelSlot implements StringRepresentable {
    TOP_LEFT(1, 1),
    TOP_RIGHT(0, 1),
    BOTTOM_LEFT(1, 0),
    BOTTOM_RIGHT(0, 0);

    public static final Codec<PanelSlot> CODEC = StringRepresentable.fromValues(PanelSlot::values);

    public static final BooleanProperty[] PROPERTIES = {
            BooleanProperty.create("slot_top_left"),
            BooleanProperty.create("slot_top_right"),
            BooleanProperty.create("slot_bottom_left"),
            BooleanProperty.create("slot_bottom_right"),
    };

    public final int xOffset;
    public final int yOffset;
    public final int modelX;
    public final int modelZ;

    PanelSlot(int xOffset, int yOffset) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.modelX = xOffset * 8;
        this.modelZ = yOffset * 8;
    }

    public BooleanProperty property() {
        return PROPERTIES[ordinal()];
    }

    @Override
    public String getSerializedName() {
        return Lang.asId(name());
    }
}
