package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
                stack.remove(LoconauticsRegistries.BOUND_BLOCK.get());
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

        Player player = context.getPlayer();
        if (state.getBlock() instanceof SmallEngineBlock || state.getBlock() instanceof TransmissionBlock) {
            if (!level.isClientSide) {
                bind(context.getItemInHand(), player, state, pos);
            }
            return InteractionResult.SUCCESS;
        }

        if (!AllBlocks.REDSTONE_LINK.has(state)) return super.useOn(context);

        boolean receiver = state.getValue(RedstoneLinkBlock.RECEIVER);
        if (!level.isClientSide) {
            if (receiver) {
                bind(context.getItemInHand(), player, state, pos);
            } else if (player != null) {
                player.displayClientMessage(Component.translatable(langPrefix() + ".not_receiver"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void bind(ItemStack stack, @Nullable Player player, BlockState state, BlockPos pos) {
        stack.set(targetComponent(), pos);
        stack.set(LoconauticsRegistries.BOUND_BLOCK.get(), BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (player != null) {
            player.displayClientMessage(Component.translatable("gui.loconautics.bound_message",
                    state.getBlock().getName()).withStyle(ChatFormatting.GREEN), true);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return boundLink(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ResourceLocation bound = stack.get(LoconauticsRegistries.BOUND_BLOCK.get());
        if (boundLink(stack) != null && bound != null) {
            tooltip.add(Component.translatable("gui.loconautics.bound_to",
                            BuiltInRegistries.BLOCK.get(bound).getName())
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
