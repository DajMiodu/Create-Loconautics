package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public abstract class BodyColorSelectorScreen extends PartSelectorScreen {

    private static final int BODY = 0;
    private static final int COLOR = 1;

    private final BodyColorModelViewer viewer;

    private final int initialBody;
    private final int initialColor;
    private final int initialStyle;
    private final int initialOptions;

    protected BodyColorSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, String titleKey,
                                      BodyColorModelViewer viewer, int body, int color, int style, int options) {
        super(menu, inventory, titleKey);
        this.viewer = viewer;
        this.initialBody = body;
        this.initialColor = color;
        this.initialStyle = style;
        this.initialOptions = options;
    }

    protected abstract String colorTooltipKey();

    protected abstract void drawIcon(GuiGraphics g, int body, int color, int style, int options,
                                     int cx, int cy, int box);

    protected abstract CustomPacketPayload applyPacket(int body, int color, int style, int options);

    @Override
    protected boolean supportsInvert() {
        return true;
    }

    @Override
    protected PartModelViewer viewer() {
        return viewer;
    }

    @Override
    protected List<ColumnSpec> columnSpecs() {
        return List.of(
                new ColumnSpec(SelectorPanel.LEFT_STRIP_X, PanelPartType.BODY_VARIANTS,
                        "gui.loconautics.selector.body",
                        (g, i, cx, cy, box) -> drawIcon(
                                g, i, selected(COLOR), selectedStyle(), selectedOptions(), cx, cy, box)),
                new ColumnSpec(SelectorPanel.RIGHT_STRIP_X, PanelPartType.PALETTE_COLORS,
                        colorTooltipKey(),
                        (g, i, cx, cy, box) -> drawIcon(
                                g, selected(BODY), i, selectedStyle(), selectedOptions(), cx, cy, box)));
    }

    @Override
    protected int initialStyle() {
        return initialStyle;
    }

    @Override
    protected int initialOptions() {
        return initialOptions;
    }

    @Override
    protected int initialSelection(int column) {
        return column == BODY ? initialBody : initialColor;
    }

    @Override
    protected void pushToViewer() {
        viewer.setBody(selected(BODY));
        viewer.setColor(selected(COLOR));
        viewer.setStyle(selectedStyle());
        viewer.setOptions(selectedOptions());
    }

    @Override
    protected void sendApply() {
        PacketDistributor.sendToServer(
                applyPacket(selected(BODY), selected(COLOR), selectedStyle(), rawOptions()));
    }
}
