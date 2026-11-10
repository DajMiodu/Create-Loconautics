package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.client.gui.selector.PartModelViewer;
import com.lycoris.loconautics.client.gui.selector.PartSelectorScreen;
import com.lycoris.loconautics.client.gui.selector.SelectorPanel;
import com.lycoris.loconautics.client.gui.selector.ValveModelViewer;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.network.packets.controls.selector.ValveSelectorApplyPacket;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class ValveSelectorScreen extends PartSelectorScreen {

    private static final int WHEEL = 0;

    private final ValveModelViewer viewer = new ValveModelViewer();

    private final int initialWheelColor;

    public ValveSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, int wheelColor) {
        super(menu, inventory, "valve_selector");
        this.initialWheelColor = wheelColor;
    }

    @Override
    protected PartModelViewer viewer() {
        return viewer;
    }

    @Override
    protected List<ColumnSpec> columnSpecs() {
        return List.of(new ColumnSpec(SelectorPanel.SOLO_STRIP_X, PanelPartType.PALETTE_COLORS,
                "gui.loconautics.selector.wheel",
                (g, i, cx, cy, box) -> ValveModelViewer.renderValveIcon(g, state(), i, cx, cy, box)));
    }

    @Override
    protected int initialSelection(int column) {
        return initialWheelColor;
    }

    @Override
    protected void pushToViewer() {
        viewer.setWheelColor(selected(WHEEL));
    }

    @Override
    protected void sendApply() {
        PacketDistributor.sendToServer(new ValveSelectorApplyPacket(
                panelPos, slot.ordinal(), selected(WHEEL)));
    }
}
