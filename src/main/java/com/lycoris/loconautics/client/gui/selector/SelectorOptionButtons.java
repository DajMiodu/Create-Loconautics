package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class SelectorOptionButtons {

    private SelectorOptionButtons() {}

    public static void add(PanelStyleSet set, int x, int y, IntSupplier mask, IntConsumer onChange,
                           Consumer<IconButton> adder) {
        for (int i = 0; i < set.optionCount(); i++) {
            int option = i;
            IconButton toggle = new SelectorSpriteButton(x + i * SelectorPanel.OPTION_STEP, y, icon(set.option(i).id()));
            toggle.green = set.isOn(mask.getAsInt(), i);
            toggle.withCallback(() -> {
                int next = set.toggle(mask.getAsInt(), option);
                onChange.accept(next);
                toggle.green = set.isOn(next, option);
            });
            toggle.setToolTip(Component.translatable(set.optionLangKey(i)));
            toggle.getToolTip().add(Component.translatable("gui.loconautics.selector.option.desc." + set.option(i).id())
                    .withStyle(ChatFormatting.GRAY));
            adder.accept(toggle);
        }
    }

    private static AllIcons icon(String id) {
        return switch (id) {
            case "plate" -> AllIcons.I_FX_SURFACE_ON;
            case "light" -> AllIcons.I_FX_FIELD_ON;
            case "glass" -> AllIcons.I_CONFIG_LOCKED;
            default -> AllIcons.I_CONFIG_OPEN;
        };
    }
}
