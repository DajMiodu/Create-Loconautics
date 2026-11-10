package com.lycoris.loconautics.content.dashboard;

import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DashboardBlockEntity extends GaugeBlockEntity {

    private static final PanelSlot[] SLOTS = {PanelSlot.TOP_RIGHT, PanelSlot.TOP_LEFT};

    public DashboardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public PanelSlot[] slots() {
        return SLOTS;
    }
}
