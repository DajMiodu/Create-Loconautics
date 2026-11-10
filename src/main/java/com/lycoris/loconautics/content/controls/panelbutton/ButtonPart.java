package com.lycoris.loconautics.content.controls.panelbutton;

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
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ButtonPart(long pressedUntil, @Nullable BlockPos link,
                         int body, int capColor, int style, int options) implements RedstoneLinkedPart {

    public static final int PRESS_TICKS = 20;

    public static final int SIGNAL = 15;

    public static final int DEFAULT_BODY = 3;

    public static final int DEFAULT_CAP_COLOR = 0;

    public static final int DEFAULT_STYLE = 0;

    public static final int DEFAULT_OPTIONS = PanelStyles.BUTTON.defaultOptions();

    public static ButtonPart read(CompoundTag tag) {
        int style = PanelPartNbt.readStyle(tag, PanelStyles.BUTTON, DEFAULT_STYLE);
        return new ButtonPart(tag.getLong("PressedUntil"),
                PanelPartNbt.readLink(tag),
                PanelPartNbt.readBody(tag, DEFAULT_BODY),
                PanelPartNbt.readColor(tag, "CapColor", DEFAULT_CAP_COLOR),
                style,
                PanelPartNbt.readOptions(tag, PanelStyles.BUTTON, DEFAULT_OPTIONS));
    }

    public boolean pressed(@Nullable Level level) {
        return level != null && level.getGameTime() < pressedUntil;
    }

    public int shownOptions() {
        return PanelStyles.BUTTON.effectiveOptions(options, style);
    }

    @Override
    public PanelGlass glass() {
        return PanelStyles.BUTTON.isOn(shownOptions(), PanelStyles.BUTTON_GLASS) ? PanelGlass.BODY : null;
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift) {
        return new ButtonPart(pressedUntil, PanelPart.followMoved(level, link, shift), body, capColor, style, options);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.BUTTON;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putLong("PressedUntil", pressedUntil);
        PanelPartNbt.writeAppearance(tag, link, body, "CapColor", capColor, style, options);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack(BoilerBlocks.GAUGE_BUTTON_ITEM.get());
        if (link != null) stack.set(LoconauticsRegistries.BUTTON_TARGET.get(), link);
        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (level == null) return;
        if (level.getGameTime() == pressedUntil) {
            level.playSound(null, panel.getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.BLOCKS, 0.35f, 1.4f);
        }
        if (link == null) return;
        driveLink(panel, slot, level, link, new ButtonPart(pressedUntil, null, body, capColor, style, options),
                signal(level));
    }

    @Override
    public int signal(Level level) {
        return pressed(level) ? SIGNAL : 0;
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        Level level = panel.getLevel();
        if (level == null) return InteractionResult.PASS;

        panel.replacePart(slot, new ButtonPart(level.getGameTime() + PRESS_TICKS, link, body, capColor, style, options));
        level.playSound(null, panel.getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_ON,
                SoundSource.BLOCKS, 0.35f, 1.4f);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
        PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return pressed(panel.getLevel()) ? 1f : 0f;
    }

    @Override
    public double chaseSpeed() {
        return 0.45;
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.button_stats").forGoggles(tooltip);
        if (!connected(panel)) {
            PanelPart.addDisconnected(tooltip);
            return;
        }
        boolean pressed = pressed(panel.getLevel());
        LoconauticsLang.translate(pressed ? "gui.goggles.button_pressed" : "gui.goggles.button_released")
                .style(pressed ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
    }
}