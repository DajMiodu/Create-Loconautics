package com.lycoris.loconautics.content.controls.panelbutton;

import com.lycoris.loconautics.client.gui.selector.BodyColorSelectorScreen;
import com.lycoris.loconautics.client.gui.selector.ButtonModelViewer;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.network.packets.controls.selector.ButtonSelectorApplyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;

public class ButtonSelectorScreen extends BodyColorSelectorScreen {

    public ButtonSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, ButtonPart part) {
        super(menu, inventory, "button_selector", new ButtonModelViewer(), part.body(), part.capColor(), part.style(), part.options());
    }

    @Override
    protected String colorTooltipKey() {
        return "gui.loconautics.selector.cap";
    }

    @Override
    protected PanelStyleSet styleSet() {
        return PanelStyles.BUTTON;
    }

    @Override
    protected void drawIcon(GuiGraphics g, int body, int color, int style, int options,
                            int cx, int cy, int box) {
        ButtonModelViewer.renderButtonIcon(g, state(), body, color, style, options, cx, cy, box);
    }

    @Override
    protected CustomPacketPayload applyPacket(int body, int color, int style, int options) {
        return new ButtonSelectorApplyPacket(panelPos, slot.ordinal(), body, color, style, options);
    }
}
