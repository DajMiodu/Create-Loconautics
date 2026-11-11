package com.lycoris.loconautics.client;

import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

public final class VariableStressTooltip implements TooltipModifier {

    private final boolean capacity;
    private final String valueKey;
    private final Object[] valueArgs;
    private final String noteKey;

    public VariableStressTooltip(boolean capacity, String valueKey, String noteKey, Object... valueArgs) {
        this.capacity = capacity;
        this.valueKey = valueKey;
        this.noteKey = noteKey;
        this.valueArgs = valueArgs;
    }

    @Override
    public void modify(ItemTooltipEvent context) {
        List<Component> tooltip = context.getToolTip();
        tooltip.add(CommonComponents.EMPTY);
        Lang.builder("create")
                .translate(capacity ? "tooltip.capacityProvided" : "tooltip.stressImpact")
                .style(ChatFormatting.GRAY)
                .addTo(tooltip);
        LoconauticsLang.translate(valueKey, valueArgs)
                .style(ChatFormatting.GOLD)
                .addTo(tooltip);
        LoconauticsLang.translate(noteKey)
                .style(ChatFormatting.DARK_GRAY)
                .addTo(tooltip);
    }
}
