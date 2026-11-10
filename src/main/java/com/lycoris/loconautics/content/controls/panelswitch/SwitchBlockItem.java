package com.lycoris.loconautics.content.controls.panelswitch;

import com.lycoris.loconautics.content.controls.panel.LinkBoundBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class SwitchBlockItem extends LinkBoundBlockItem {

    public SwitchBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected DataComponentType<BlockPos> targetComponent() {
        return LoconauticsRegistries.SWITCH_TARGET.get();
    }

    @Override
    protected String langPrefix() {
        return "gui.loconautics.gauge_switch";
    }

    @Override
    protected PanelPart partFor(ItemStack stack) {
        return new SwitchPart(0, boundLink(stack), SwitchPart.DEFAULT_BODY, SwitchPart.DEFAULT_KNOB_COLOR, SwitchPart.DEFAULT_STYLE, SwitchPart.DEFAULT_OPTIONS);
    }
}
