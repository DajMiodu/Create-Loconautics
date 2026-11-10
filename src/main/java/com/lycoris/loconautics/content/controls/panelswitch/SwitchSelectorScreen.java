package com.lycoris.loconautics.content.controls.panelswitch;

import com.lycoris.loconautics.client.gui.selector.BodyColorSelectorScreen;
import com.lycoris.loconautics.client.gui.selector.SwitchModelViewer;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.network.packets.controls.selector.SwitchSelectorApplyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;

public class SwitchSelectorScreen extends BodyColorSelectorScreen {

    public SwitchSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, SwitchPart part) {
        super(menu, inventory, "switch_selector", new SwitchModelViewer(), part.body(), part.knobColor(), part.style(), part.options());
    }

    @Override
    protected String colorTooltipKey() {
        return "gui.loconautics.selector.knob";
    }

    @Override
    protected PanelStyleSet styleSet() {
        return PanelStyles.SWITCH;
    }

    @Override
    protected void drawIcon(GuiGraphics g, int body, int color, int style, int options,
                            int cx, int cy, int box) {
        SwitchModelViewer.renderSwitchIcon(g, state(), body, color, style, options, cx, cy, box);
    }

    @Override
    protected CustomPacketPayload applyPacket(int body, int color, int style, int options) {
        return new SwitchSelectorApplyPacket(panelPos, slot.ordinal(), body, color, style, options);
    }
}
