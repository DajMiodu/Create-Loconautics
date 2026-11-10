package com.lycoris.loconautics.content.controls.panellever;

import com.lycoris.loconautics.client.gui.selector.BodyColorSelectorScreen;
import com.lycoris.loconautics.client.gui.selector.LeverModelViewer;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.network.packets.controls.selector.LeverSelectorApplyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;

public class LeverSelectorScreen extends BodyColorSelectorScreen {

    public LeverSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, LeverPart part) {
        super(menu, inventory, "lever_selector", new LeverModelViewer(), part.body(), part.knobColor(), part.style(), part.options());
    }

    @Override
    protected String colorTooltipKey() {
        return "gui.loconautics.selector.knob";
    }

    @Override
    protected PanelStyleSet styleSet() {
        return PanelStyles.LEVER;
    }

    @Override
    protected void drawIcon(GuiGraphics g, int body, int color, int style, int options,
                            int cx, int cy, int box) {
        LeverModelViewer.renderLeverIcon(g, state(), body, color, style, options, cx, cy, box);
    }

    @Override
    protected CustomPacketPayload applyPacket(int body, int color, int style, int options) {
        return new LeverSelectorApplyPacket(panelPos, slot.ordinal(), body, color, style, options);
    }
}
