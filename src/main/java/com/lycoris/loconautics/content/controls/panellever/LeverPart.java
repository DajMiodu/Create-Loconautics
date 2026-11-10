package com.lycoris.loconautics.content.controls.panellever;

import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.ControlsBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartNbt;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.RedstoneLinkedPart;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record LeverPart(int power, @Nullable BlockPos link, int body, int knobColor, int style, int options) implements RedstoneLinkedPart {

    public static final int MAX_POWER = 15;

    public static final int DEFAULT_BODY = 3;

    public static final int DEFAULT_KNOB_COLOR = 0;

    public static final int DEFAULT_STYLE = 0;

    public static final int DEFAULT_OPTIONS = PanelStyles.LEVER.defaultOptions();

    public static int clamp(int power) {
        return Mth.clamp(power, 0, MAX_POWER);
    }

    public static LeverPart read(CompoundTag tag) {
        int style = PanelPartNbt.readStyle(tag, PanelStyles.LEVER, DEFAULT_STYLE);
        return new LeverPart(clamp(tag.getInt("Power")),
                PanelPartNbt.readLink(tag),
                PanelPartNbt.readBody(tag, DEFAULT_BODY),
                PanelPartNbt.readColor(tag, "KnobColor", DEFAULT_KNOB_COLOR),
                style,
                PanelPartNbt.readOptions(tag, PanelStyles.LEVER, DEFAULT_OPTIONS));
    }

    public float fraction() {
        return power / (float) MAX_POWER;
    }

    public int shownOptions() {
        return PanelStyles.LEVER.effectiveOptions(options, style);
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift) {
        return new LeverPart(power, PanelPart.followMoved(level, link, shift), body, knobColor, style, options);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.LEVER;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putInt("Power", power);
        PanelPartNbt.writeAppearance(tag, link, body, "KnobColor", knobColor, style, options);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack(ControlsBlocks.GAUGE_LEVER_ITEM.get());
        if (link != null) stack.set(LoconauticsRegistries.LEVER_TARGET.get(), link);
        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (link == null || level == null) return;
        driveLink(panel, slot, level, link, new LeverPart(power, null, body, knobColor, style, options), power);
    }

    @Override
    public int signal(Level level) {
        return power;
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        return InteractionResult.CONSUME;
    }

    @Override
    public void clientUse(PanelBlockEntity panel, PanelSlot slot, Player player) {
        if (panel.getLevel() == null || !player.isLocalPlayer()) return;
        LeverHoldInteraction.grab(panel.getLevel(), player, panel.getBlockPos(), slot, power,
                panel.isInverted(slot));
    }

    @Override
    public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
        PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return fraction();
    }

    @Override
    public double chaseSpeed() {
        return 0.4;
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.lever_stats").forGoggles(tooltip);
        if (!connected(panel)) {
            PanelPart.addDisconnected(tooltip);
            return;
        }
        LoconauticsLang.builder().text(power + " / " + MAX_POWER)
                .style(power > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
    }
}