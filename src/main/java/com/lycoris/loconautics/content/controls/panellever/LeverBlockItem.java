package com.lycoris.loconautics.content.controls.panellever;

import com.lycoris.loconautics.content.controls.panel.LinkBoundBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class LeverBlockItem extends LinkBoundBlockItem {

    public LeverBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected DataComponentType<BlockPos> targetComponent() {
        return LoconauticsRegistries.LEVER_TARGET.get();
    }

    @Override
    protected String langPrefix() {
        return "gui.loconautics.lever";
    }

    @Override
    protected PanelPart partFor(ItemStack stack) {
        return new LeverPart(0, boundLink(stack), LeverPart.DEFAULT_BODY, LeverPart.DEFAULT_KNOB_COLOR, LeverPart.DEFAULT_STYLE, LeverPart.DEFAULT_OPTIONS);
    }
}
