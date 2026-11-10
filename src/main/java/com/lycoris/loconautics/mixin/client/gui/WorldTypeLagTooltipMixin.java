package com.lycoris.loconautics.mixin.client.gui;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractWidget.class)
public abstract class WorldTypeLagTooltipMixin {

    @Unique
    private static final ResourceKey<WorldPreset> LOCOMOTIVE_READY =
            ResourceKey.create(Registries.WORLD_PRESET, LoconauticsConstants.id("locomotive_ready"));

    @ModifyVariable(method = "setTooltip", at = @At("HEAD"), argsOnly = true)
    private Tooltip loconautics$lagWarning(Tooltip tooltip) {
        if ((Object) this instanceof CycleButton<?> button
                && button.getValue() instanceof WorldCreationUiState.WorldTypeEntry entry
                && entry.preset() != null
                && entry.preset().is(LOCOMOTIVE_READY)) {
            return Tooltip.create(Component.translatable("generator.loconautics.locomotive_ready.info"));
        }
        return tooltip;
    }
}
