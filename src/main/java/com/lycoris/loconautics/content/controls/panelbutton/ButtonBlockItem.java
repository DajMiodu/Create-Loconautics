package com.lycoris.loconautics.content.controls.panelbutton;

import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.content.controls.panel.LinkBoundBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class ButtonBlockItem extends LinkBoundBlockItem {

    public ButtonBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected DataComponentType<BlockPos> targetComponent() {
        return LoconauticsRegistries.BUTTON_TARGET.get();
    }

    @Override
    protected String langPrefix() {
        return "gui.loconautics.button";
    }

    @Override
    protected void onPartPlaced(Player player, BlockPos pos, PanelSlot slot, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer) {
            TrainAdvancements.buttonPlaced(serverPlayer);
        }
    }

    @Override
    protected PanelPart partFor(ItemStack stack) {
        return new ButtonPart(0L, boundLink(stack), ButtonPart.DEFAULT_BODY, ButtonPart.DEFAULT_CAP_COLOR,
                ButtonPart.DEFAULT_STYLE, ButtonPart.DEFAULT_OPTIONS);
    }
}
