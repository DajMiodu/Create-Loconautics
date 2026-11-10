package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.foundation.LoconauticsLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PanelPart {

    PanelPartType type();

    void write(CompoundTag tag);

    ItemStack asStack();

    InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player);

    default InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
        return InteractionResult.PASS;
    }

    float animationTarget(PanelBlockEntity panel);

    @Nullable
    default PanelGlass glass() {
        return null;
    }

    default float lightLevel(PanelBlockEntity panel, PanelSlot slot) {
        return 0f;
    }

    default void serverTick(PanelBlockEntity panel, PanelSlot slot) {
    }

    default void onRemoved(PanelBlockEntity panel) {
    }

    default void clientUse(PanelBlockEntity panel, PanelSlot slot, Player player) {
    }

    default boolean bindFromHeld(PanelBlockEntity panel, PanelSlot slot, Player player) {
        return false;
    }

    default boolean consumesUse() {
        return true;
    }

    default double chaseSpeed() {
        return 0.08;
    }

    default LerpedFloat newAnimation() {
        return LerpedFloat.linear().startWithValue(0);
    }

    default void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
    }

    default PanelPart relocated(Level level, BlockPos shift) {
        return this;
    }

    @Nullable
    static BlockPos followMoved(Level level, @Nullable BlockPos target, BlockPos shift) {
        if (target == null || !level.isLoaded(target) || !level.getBlockState(target).isAir()) return target;
        return target.offset(shift);
    }

    static void addDisconnected(List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.panel_disconnected")
                .style(ChatFormatting.RED).forGoggles(tooltip, 1);
    }
}
