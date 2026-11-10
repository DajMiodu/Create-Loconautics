package com.lycoris.loconautics.content.controls.panelswitch;

import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartNbt;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.RedstoneLinkedPart;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.registry.LoconauticsSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record SwitchPart(int power, @Nullable BlockPos link, int body, int knobColor, int style, int options) implements RedstoneLinkedPart {

    public static final int MAX_POWER = 15;

    public static final int DEFAULT_BODY = 3;

    public static final int DEFAULT_KNOB_COLOR = 0;

    public static final int DEFAULT_STYLE = 0;

    public static final int DEFAULT_OPTIONS = PanelStyles.SWITCH.defaultOptions();

    public static SwitchPart read(CompoundTag tag) {
        int style = PanelPartNbt.readStyle(tag, PanelStyles.SWITCH, DEFAULT_STYLE);
        return new SwitchPart(tag.getInt("Power") > 0 ? MAX_POWER : 0,
                PanelPartNbt.readLink(tag),
                PanelPartNbt.readBody(tag, DEFAULT_BODY),
                PanelPartNbt.readColor(tag, "KnobColor", DEFAULT_KNOB_COLOR),
                style,
                PanelPartNbt.readOptions(tag, PanelStyles.SWITCH, DEFAULT_OPTIONS));
    }

    public boolean on() {
        return power > 0;
    }

    public float fraction() {
        return on() ? 1f : 0f;
    }

    public int shownOptions() {
        return PanelStyles.SWITCH.effectiveOptions(options, style);
    }

    @Override
    public PanelGlass glass() {
        return PanelStyles.SWITCH.isOn(shownOptions(), PanelStyles.SWITCH_GLASS) ? PanelGlass.SLIM : null;
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift) {
        return new SwitchPart(power, PanelPart.followMoved(level, link, shift), body, knobColor, style, options);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.SWITCH;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putInt("Power", power);
        PanelPartNbt.writeAppearance(tag, link, body, "KnobColor", knobColor, style, options);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack(BoilerBlocks.GAUGE_SWITCH_ITEM.get());
        if (link != null) stack.set(LoconauticsRegistries.SWITCH_TARGET.get(), link);
        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (link == null || level == null) return;

        driveLink(panel, slot, level, link, new SwitchPart(power, null, body, knobColor, style, options), power);
    }

    @Override
    public void driveTransmission(TransmissionBlockEntity transmission, int signal) {
        transmission.driveDirectionFromPanel(signal > 0);
    }

    @Override
    public int engineSignal(int signal) {
        return MAX_SIGNAL - signal;
    }

    @Override
    public int signal(Level level) {
        return power;
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        Level level = panel.getLevel();
        if (level == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        boolean next = !on();
        panel.replacePart(slot, new SwitchPart(next ? MAX_POWER : 0, link, body, knobColor, style, options));
        level.playSound(null, panel.getBlockPos(), LoconauticsSounds.SWITCH_TOGGLE.get(), SoundSource.BLOCKS,
                0.6f, next ? 1.0f : 0.85f);
        return InteractionResult.CONSUME;
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
        LoconauticsLang.translate("gui.goggles.switch_stats").forGoggles(tooltip);
        if (!connected(panel)) {
            PanelPart.addDisconnected(tooltip);
            return;
        }
        LoconauticsLang.translate(on() ? "gui.goggles.switch_on" : "gui.goggles.switch_off")
                .style(on() ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
    }
}