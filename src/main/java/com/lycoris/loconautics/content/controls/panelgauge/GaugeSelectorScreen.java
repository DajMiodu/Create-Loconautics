package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.client.gui.gauge.GaugeModelViewer;
import com.lycoris.loconautics.client.gui.selector.GaugeCompatButton;
import com.lycoris.loconautics.client.gui.selector.PartModelViewer;
import com.lycoris.loconautics.client.gui.selector.PartSelectorScreen;
import com.lycoris.loconautics.client.gui.selector.SelectorPanel;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.network.packets.controls.LocomotiveGaugeSelectPacket;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class GaugeSelectorScreen extends PartSelectorScreen {

    private static final int BACKGROUND = 0;
    private static final int FRONT = 1;
    private static final int NEEDLE = 2;

    private static final int BACKGROUND_VARIANTS = 36;
    private static final int FRONT_VARIANTS = 4;

    private GaugeMetric metric;
    private final boolean bound;
    private GaugeCompatButton compatButton;
    private final GaugeModelViewer viewer = new GaugeModelViewer();

    private final int initialBackground;
    private final int initialFront;
    private final int initialNeedleColor;
    private final int initialOptions;

    public GaugeSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, GaugeMetric metric,
                               boolean bound, int background, int front, int needleColor, int options) {
        super(menu, inventory, "gauge_selector");
        this.metric = metric;
        this.bound = bound;
        this.initialBackground = background;
        this.initialFront = front;
        this.initialNeedleColor = needleColor;
        this.initialOptions = options;
    }

    @Override
    protected PartModelViewer viewer() {
        return viewer;
    }

    @Override
    protected List<ColumnSpec> columnSpecs() {
        return List.of(
                new ColumnSpec(SelectorPanel.TRIPLE_LEFT_STRIP_X, BACKGROUND_VARIANTS,
                        "gui.loconautics.selector.background",
                        (g, i, cx, cy, box) -> GaugeModelViewer.renderGaugeIcon(
                                g, state(), i, selected(FRONT), selected(NEEDLE), selectedOptions(), cx, cy, box)),
                new ColumnSpec(SelectorPanel.TRIPLE_MID_STRIP_X, FRONT_VARIANTS,
                        "gui.loconautics.selector.front",
                        (g, i, cx, cy, box) -> GaugeModelViewer.renderGaugeIcon(
                                g, state(), selected(BACKGROUND), i, selected(NEEDLE), selectedOptions(), cx, cy, box)),
                new ColumnSpec(SelectorPanel.TRIPLE_RIGHT_STRIP_X, GaugeNeedle.COLORS,
                        "gui.loconautics.selector.needle",
                        (g, i, cx, cy, box) -> GaugeModelViewer.renderGaugeIcon(
                                g, state(), selected(BACKGROUND), selected(FRONT), i, selectedOptions(), cx, cy, box)));
    }

    @Override
    protected boolean hasMetricBar() {
        return !bound || GaugeMetric.valuesOf(metric.group()).length > 1;
    }

    @Override
    protected void addExtraControls() {
        compatButton = new GaugeCompatButton(guiLeft + SelectorPanel.CONFIRM_X - 28,
                guiTop + SelectorPanel.CONFIRM_Y, hintId());
        addRenderableWidget(compatButton);
        if (!bound) {
            int labelX = guiLeft + SelectorPanel.METRIC_X + 4;
            Label unbound = new Label(labelX, guiTop + SelectorPanel.METRIC_Y + 5, Component.empty()).withShadow();
            unbound.text = Component.translatable("gui.loconautics.selector.bind");
            addRenderableWidget(unbound);
            return;
        }
        GaugeMetric[] choices = GaugeMetric.valuesOf(metric.group());
        if (choices.length < 2) return;
        List<Component> names = new ArrayList<>();
        for (GaugeMetric choice : choices) {
            names.add(Component.translatable("loconautics." + choice.langKey()));
        }
        int x = guiLeft + SelectorPanel.METRIC_X;
        int y = guiTop + SelectorPanel.METRIC_Y;
        Label label = new Label(x + 4, y + 5, Component.empty()).withShadow();
        SelectionScrollInput input = new SelectionScrollInput(x, y, SelectorPanel.METRIC_W, SelectorPanel.BUTTON_SIZE);
        input.forOptions(names)
                .titled(Component.translatable("gui.loconautics.selector.metric"))
                .writingTo(label)
                .calling(index -> {
                    metric = choices[Math.max(0, Math.min(choices.length - 1, index))];
                    setSelected(BACKGROUND, metric.defaultBackground());
                })
                .setState(indexOf(choices, metric));
        addRenderableWidget(input);
        addRenderableWidget(label);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        compatButton.renderTooltips(graphics, font, mouseX, mouseY);
    }

    private String hintId() {
        return "gauge_compat:" + Minecraft.getInstance().level.dimension().location() + ":"
                + panelPos.asLong() + ":" + slot.ordinal();
    }

    private static int indexOf(GaugeMetric[] choices, GaugeMetric metric) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == metric) return i;
        }
        return 0;
    }

    @Override
    protected PanelStyleSet styleSet() {
        return PanelStyles.GAUGE;
    }

    @Override
    protected int optionX(PanelStyleSet set) {
        return SelectorPanel.PANEL_W - SelectorPanel.CONFIRM_X - SelectorPanel.BUTTON_SIZE;
    }

    @Override
    protected int initialOptions() {
        return initialOptions;
    }

    @Override
    protected int initialSelection(int column) {
        return switch (column) {
            case BACKGROUND -> initialBackground;
            case FRONT -> initialFront;
            default -> initialNeedleColor;
        };
    }

    @Override
    protected void pushToViewer() {
        viewer.setBackground(selected(BACKGROUND));
        viewer.setFront(selected(FRONT));
        viewer.setNeedleColor(selected(NEEDLE));
        viewer.setOptions(selectedOptions());
    }

    @Override
    protected void sendApply() {
        PacketDistributor.sendToServer(new LocomotiveGaugeSelectPacket(panelPos, slot.ordinal(), metric,
                selected(BACKGROUND), selected(FRONT), selected(NEEDLE), selectedOptions()));
    }
}
