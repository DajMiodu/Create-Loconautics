package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GaugeBlockItem extends PanelBlockItem {

    public GaugeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    public static BlockPos boundFirebox(ItemStack stack) {
        return stack.get(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get());
    }

    public static GaugeMetric boundMetric(ItemStack stack) {
        GaugeMetric metric = stack.get(LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get());
        return metric == null ? GaugeMetric.SPEEDOMETER_RPM : metric;
    }

    public static ItemStack stackFor(GaugeBlockEntity.Gauge gauge) {
        ItemStack stack = new ItemStack(
                com.lycoris.loconautics.content.controls.ControlsBlocks.LOCOMOTIVE_GAUGE_ITEM.get());
        if (gauge != null && gauge.bound()) {
            stack.set(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get(), gauge.firebox());
            stack.set(LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get(), gauge.metric());
        }
        return stack;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && boundFirebox(stack) != null) {
            if (!level.isClientSide) {
                stack.remove(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get());
                player.displayClientMessage(
                        Component.translatable("gui.loconautics.locomotive_gauge.cleared"), true);
            }
            return net.minecraft.world.InteractionResultHolder.success(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        Player shifter = context.getPlayer();
        if (shifter != null && shifter.isShiftKeyDown() && boundFirebox(context.getItemInHand()) != null) {
            if (!level.isClientSide) {
                context.getItemInHand().remove(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get());
                shifter.displayClientMessage(
                        Component.translatable("gui.loconautics.locomotive_gauge.cleared"), true);
            }
            return InteractionResult.SUCCESS;
        }

        GaugeMetric.Group group = GaugeMetric.Group.of(level.getBlockEntity(pos));
        if (group != null) {
            if (!level.isClientSide) {
                ItemStack stack = context.getItemInHand();
                stack.set(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get(), pos);
                stack.set(LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get(),
                        GaugeMetric.defaultFor(group));
            }
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    @Nullable
    protected PanelPart partFor(ItemStack stack) {
        BlockPos firebox = boundFirebox(stack);
        GaugeMetric metric = boundMetric(stack);
        int background = firebox == null ? GaugeBlockEntity.DEFAULT_BACKGROUND : metric.defaultBackground();
        return new GaugeBlockEntity.Gauge(firebox == null ? GaugeBlockEntity.Gauge.UNBOUND : firebox, metric, background,
                GaugeBlockEntity.DEFAULT_FRONT, GaugeBlockEntity.DEFAULT_NEEDLE_COLOR,
                GaugeBlockEntity.DEFAULT_OPTIONS);
    }

    @Override
    protected void onPartPlaced(Player player, BlockPos pos, PanelSlot slot, ItemStack stack) {
        PanelFrequencyMenu.open(player, pos, slot);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockPos firebox = boundFirebox(stack);
        if (firebox != null) {
            tooltip.add(Component.translatable("gui.loconautics.locomotive_gauge.bound_to",
                            firebox.getX(), firebox.getY(), firebox.getZ())
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }
}
