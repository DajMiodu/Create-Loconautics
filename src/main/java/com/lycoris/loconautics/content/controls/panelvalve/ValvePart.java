package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.ControlsBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelDriveBus;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.createmod.catnip.animation.LerpedFloat;
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

public record ValvePart(float degrees, @Nullable BlockPos safetyValve, int wheelColor) implements PanelPart {

    public static final float FULL_TURN = 360f;

    public static final float SNAP = 45f;

    public static final int DEFAULT_WHEEL_COLOR = 1;

    public static float wrap(float degrees) {
        float wrapped = degrees % FULL_TURN;
        return wrapped < 0 ? wrapped + FULL_TURN : wrapped;
    }

    public static float clamp(float degrees) {
        return Mth.clamp(degrees, 0f, FULL_TURN);
    }

    public static ValvePart read(CompoundTag tag) {
        return new ValvePart(clamp(tag.getFloat("Degrees")),
                tag.contains("SafetyValve") ? BlockPos.of(tag.getLong("SafetyValve")) : null,
                Mth.clamp(tag.contains("WheelColor") ? tag.getInt("WheelColor") : DEFAULT_WHEEL_COLOR,
                        0, PanelPartType.PALETTE_COLORS - 1));
    }

    public float fraction() {
        return degrees / FULL_TURN;
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift) {
        return new ValvePart(degrees, PanelPart.followMoved(level, safetyValve, shift), wheelColor);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.VALVE;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Degrees", degrees);
        if (safetyValve != null) tag.putLong("SafetyValve", safetyValve.asLong());
        tag.putInt("WheelColor", wheelColor);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack(ControlsBlocks.VALVE_ITEM.get());
        if (safetyValve != null) {
            stack.set(LoconauticsRegistries.VALVE_TARGET.get(), safetyValve);
        }
        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (safetyValve == null || level == null) return;
        if (level.isLoaded(safetyValve)) {
            PanelDriveBus.release(level, safetyValve);
            panel.replacePart(slot, new ValvePart(degrees, null, wheelColor));
        }
    }

    @Override
    public void onRemoved(PanelBlockEntity panel) {
        Level level = panel.getLevel();
        if (safetyValve == null || level == null) return;
        PanelDriveBus.release(level, safetyValve);
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        ValveMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
        PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return degrees;
    }

    @Override
    public double chaseSpeed() {
        return 0.35;
    }

    @Override
    public LerpedFloat newAnimation() {
        return LerpedFloat.angular().startWithValue(degrees);
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.valve_stats").forGoggles(tooltip);
        if (safetyValve == null) {
            PanelPart.addDisconnected(tooltip);
            return;
        }
        Level level = panel.getLevel();
        if (level != null) {
            LoconauticsLang.translate("gui.goggles.valve_linked_to",
                            level.getBlockState(safetyValve).getBlock().getName())
                    .style(ChatFormatting.GRAY).forGoggles(tooltip);
        }
        LoconauticsLang.builder().text(Mth.floor(degrees) + "°")
                .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
    }
}