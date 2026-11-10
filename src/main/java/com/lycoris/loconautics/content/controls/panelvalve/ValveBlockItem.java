package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlock;
import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlock;
import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
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

public class ValveBlockItem extends PanelBlockItem {

    public ValveBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    public static BlockPos boundValve(ItemStack stack) {
        return stack.get(LoconauticsRegistries.VALVE_TARGET.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && boundValve(stack) != null) {
            if (!level.isClientSide) {
                stack.remove(LoconauticsRegistries.VALVE_TARGET.get());
                player.displayClientMessage(Component.translatable("gui.loconautics.valve.cleared"), true);
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

        Player shifter = context.getPlayer();
        if (shifter != null && shifter.isShiftKeyDown() && boundValve(context.getItemInHand()) != null) {
            if (!level.isClientSide) {
                context.getItemInHand().remove(LoconauticsRegistries.VALVE_TARGET.get());
                shifter.displayClientMessage(Component.translatable("gui.loconautics.valve.cleared"), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (state.getBlock() instanceof SafetyValveBlock || state.getBlock() instanceof SteamTakeoffBlock
                || state.getBlock() instanceof SmallEngineBlock) {
            if (!level.isClientSide) {
                context.getItemInHand().set(LoconauticsRegistries.VALVE_TARGET.get(), pos);
                Player player = context.getPlayer();
                if (player != null) {
                    player.displayClientMessage(Component.translatable("gui.loconautics.valve.bound"), true);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    @Nullable
    protected PanelPart partFor(ItemStack stack) {
        BlockPos valve = boundValve(stack);
        return valve == null ? null : new ValvePart(0f, valve, ValvePart.DEFAULT_WHEEL_COLOR);
    }

    @Override
    protected void notifyCannotPlace(Player player) {
        player.displayClientMessage(Component.translatable("gui.loconautics.valve.unbound"), true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return boundValve(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockPos valve = boundValve(stack);
        if (valve != null) {
            tooltip.add(Component.translatable("gui.loconautics.valve.bound_to",
                            valve.getX(), valve.getY(), valve.getZ())
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
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
