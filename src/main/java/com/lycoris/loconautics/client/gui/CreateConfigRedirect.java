package com.lycoris.loconautics.client.gui;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.mixin.client.gui.SubMenuConfigScreenAccessor;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.SubMenuConfigScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.DelegatedStencilElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class CreateConfigRedirect {

    private static final String CREATE_ID = "create";
    private static final String TRAINS_TITLE = "Trains";

    private CreateConfigRedirect() {
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof SubMenuConfigScreen screen)
                || screen.type != ModConfig.Type.SERVER
                || !CREATE_ID.equals(ConfigScreen.modID)) {
            return;
        }
        SubMenuConfigScreenAccessor access = (SubMenuConfigScreenAccessor) screen;
        ConfigScreenList list = access.loconautics$getList();
        if (!TRAINS_TITLE.equals(access.loconautics$getTitle()) || list == null) {
            return;
        }
        list.children().add(0, new LoconauticsEntry(screen));
    }

    private static final class LoconauticsEntry extends ConfigScreenList.LabeledEntry {

        private final BoxWidget button = new BoxWidget(0, 0, 35, 16);

        private LoconauticsEntry(Screen parent) {
            super(Component.translatable("loconautics.configuration.open_from_create").getString());
            button.showingElement(PonderGuiTextures.ICON_CONFIG_OPEN.asStencil().at(10.0f, 0.0f));
            button.modifyElement(e -> ((DelegatedStencilElement) e).withElementRenderer(BoxWidget.gradientFactory.apply(button)));
            button.withCallback(() -> ScreenOpener.open(new BaseConfigScreen(parent, LoconauticsConstants.MOD_ID)));
            listeners.add(button);
        }

        @Override
        public void tick() {
            super.tick();
            button.tick();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY,
                boolean hovered, float partialTicks) {
            super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
            button.setX(x + width - 108);
            button.setY(y + 10);
            button.setHeight(height - 20);
            button.render(graphics, mouseX, mouseY, partialTicks);
        }

        @Override
        protected int getLabelWidth(int totalWidth) {
            return (int) (totalWidth * 0.4f) + 30;
        }
    }
}
