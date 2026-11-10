package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import net.createmod.catnip.lang.Lang;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public enum PanelPartType implements StringRepresentable {
    GAUGE(GaugeBlockEntity.Gauge::read, PanelBlock.FLAT_SLOT_BOX),
    VALVE(ValvePart::read, new double[]{1, 0, 1, 7, 2.75, 7}),
    BUTTON(ButtonPart::read, PanelBlock.FLAT_SLOT_BOX),
    LEVER(LeverPart::read, new double[]{2, 0, 1, 6, 2, 7}),
    SWITCH(com.lycoris.loconautics.content.controls.panelswitch.SwitchPart::read,
            new double[]{2, 0, 1, 6, 2, 7});

    public static final int PALETTE_COLORS = 35;

    public static final int BODY_VARIANTS = 4;

    public static int clampColor(int color) {
        return Mth.clamp(color, 0, PALETTE_COLORS - 1);
    }

    public static int clampBody(int body) {
        return Mth.clamp(body, 0, BODY_VARIANTS - 1);
    }

    private final Function<CompoundTag, PanelPart> reader;

    private final double[] slotBox;

    private VoxelShape[][][] shapes;

    private VoxelShape[][][] centeredShapes;

    PanelPartType(Function<CompoundTag, PanelPart> reader, double[] slotBox) {
        this.reader = reader;
        this.slotBox = slotBox;
    }

    public PanelPart read(CompoundTag tag) {
        return reader.apply(tag);
    }

    public AABB localBox(double offsetX, double offsetZ) {
        return new AABB(
                (slotBox[0] + offsetX) / 16, slotBox[1] / 16, (slotBox[2] + offsetZ) / 16,
                (slotBox[3] + offsetX) / 16, slotBox[4] / 16, (slotBox[5] + offsetZ) / 16);
    }

    public VoxelShape[][][] shapes() {
        if (shapes == null) shapes = PanelBlock.buildShapes(slotBox);
        return shapes;
    }

    public VoxelShape[][][] centeredShapes() {
        if (centeredShapes == null) centeredShapes = PanelBlock.buildShapes(slotBox, true);
        return centeredShapes;
    }

    public static PanelPartType byName(@Nullable String name) {
        if (name == null || name.isEmpty()) return GAUGE;
        for (PanelPartType type : values()) {
            if (type.getSerializedName().equals(name)) return type;
        }
        return GAUGE;
    }

    @Override
    public String getSerializedName() {
        return Lang.asId(name());
    }
}
