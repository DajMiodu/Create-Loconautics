package com.lycoris.loconautics.client.gui;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import org.jetbrains.annotations.Nullable;

public final class LoconauticsConfigScreen extends ConfigurationSectionScreen {
    private static final String TITLE_KEY = LoconauticsConstants.MOD_ID + ".configuration.title";
    private static final String SECTION_FORMAT = "neoforge.configuration.uitext.section";
    private static final String EDIT_LABEL = "neoforge.configuration.uitext.sectiontext";

    public static Screen create(ModContainer container, Screen parent) {
        return new ConfigurationScreen(container, parent, LoconauticsConfigScreen::new);
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ConfigurationScreen screen)
                || !(screen.getTitle().getContents() instanceof TranslatableContents title)
                || !TITLE_KEY.equals(title.getKey())) {
            return;
        }

        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof OptionsList options) {
                for (ContainerEventHandler row : options.children()) {
                    for (GuiEventListener child : row.children()) {
                        if (child instanceof Button button) {
                            button.setMessage(unwrapSectionLabel(button.getMessage()));
                        }
                    }
                }
            }
        }
    }

    private static Component unwrapSectionLabel(Component message) {
        if (message.getContents() instanceof TranslatableContents translation
                && SECTION_FORMAT.equals(translation.getKey())
                && translation.getArgs().length == 1
                && translation.getArgs()[0] instanceof Component label) {
            return label;
        }
        return message;
    }

    private LoconauticsConfigScreen(ConfigurationScreen parent, ModConfig.Type type, ModConfig config, Component title) {
        super(parent, type, config, title);
    }

    private LoconauticsConfigScreen(Context parentContext, Screen parent, UnmodifiableConfig subconfig, String key,
                                    UnmodifiableConfig subsection, Component title) {
        super(parentContext, parent, subconfig.valueMap(), key, subsection.entrySet(), title);
    }

    @Override
    @Nullable
    protected Element createSection(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
        if (subconfig.isEmpty()) {
            return null;
        }
        Component label = getTranslationComponent(key);
        Component tooltip = getTooltipComponent(key, null);
        String buttonKey = getTranslationKey(key) + ".button";
        Component editLabel = Component.translatable(I18n.exists(buttonKey) ? buttonKey : EDIT_LABEL);
        Button edit = Button.builder(editLabel, button -> {
                    ConfigurationSectionScreen target = sectionCache.computeIfAbsent(key,
                            section -> new LoconauticsConfigScreen(context, this, subconfig, section, subsection, label).rebuild());
                    minecraft.setScreen(target);
                })
                .tooltip(Tooltip.create(tooltip))
                .width(Button.DEFAULT_WIDTH)
                .build();
        return new Element(label, tooltip, edit, false);
    }
}
