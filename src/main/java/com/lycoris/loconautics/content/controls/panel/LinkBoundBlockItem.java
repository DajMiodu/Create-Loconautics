package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public abstract class LinkBoundBlockItem extends PanelBlockItem {

    protected LinkBoundBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    protected abstract DataComponentType<BlockPos> targetComponent();

    protected abstract String langPrefix();

    @Nullable
    public BlockPos boundLink(ItemStack stack) {
        return stack.get(targetComponent());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && boundLink(stack) != null) {
            if (!level.isClientSide) {
                stack.remove(targetComponent());
                player.displayClientMessage(Component.translatable(langPrefix() + ".cleared"), true);
            }
            return InteractionResultHolder.success(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (!AllBlocks.REDSTONE_LINK.has(state)) return super.useOn(context);

        Player player = context.getPlayer();
        boolean receiver = state.getValue(RedstoneLinkBlock.RECEIVER);
        if (!level.isClientSide) {
            if (receiver) context.getItemInHand().set(targetComponent(), pos);
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        langPrefix() + (receiver ? ".bound" : ".not_receiver")), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return boundLink(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockPos link = boundLink(stack);
        if (link != null) {
            tooltip.add(Component.translatable(langPrefix() + ".bound_to",
                            link.getX(), link.getY(), link.getZ())
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }
}
