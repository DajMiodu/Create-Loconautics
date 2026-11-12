package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.content.frequency.BindFrequencyScreen;
import com.lycoris.loconautics.trainphysics.actors.TrainActorControls;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AnalogControllerScreen
        extends BindFrequencyScreen<AnalogControllerBlockEntity, AnalogControllerMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "loconautics", "textures/gui/frequency/frequency_bind_4.png");

    private static final String AUTOFILL_KEY = "gui.loconautics.analog_controller.autofill";
    private static final int AUTOFILL_X = 179;
    private static final int AUTOFILL_Y = 32;

    public AnalogControllerScreen(AnalogControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, TEXTURE, 214, 153, 214, 153, 19, 158, 119, 157, 179);
    }

    @Override
    protected void init() {
        super.init();
        AnalogControllerBlockEntity controller = menu.contentHolder;
        if (controller == null || !TrainActorControls.onTrain(controller.getLevel(), controller.getBlockPos())) {
            return;
        }
        IconButton autofill = new IconButton(leftPos + AUTOFILL_X, topPos + AUTOFILL_Y, AllIcons.I_REFRESH);
        autofill.setToolTip(Component.translatable(AUTOFILL_KEY));
        autofill.getToolTip().add(Component.translatable(AUTOFILL_KEY + ".desc").withStyle(ChatFormatting.GRAY));
        autofill.withCallback(() -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                AnalogControllerMenu.AUTOFILL_BUTTON));
        addRenderableWidget(autofill);
    }
}
