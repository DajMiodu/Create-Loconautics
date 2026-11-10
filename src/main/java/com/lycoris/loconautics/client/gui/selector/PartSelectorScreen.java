package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.client.gui.gauge.GaugePartColumn;
import com.lycoris.loconautics.content.controls.ControlsBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelPartLayout;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.network.packets.controls.selector.PanelInvertPacket;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class PartSelectorScreen extends AbstractSimiContainerScreen<PanelFrequencyMenu> {

    public record ColumnSpec(int stripX, int count, String tooltipKey,
                             GaugePartColumn.SlotRenderer renderer) {
    }

    protected final BlockPos panelPos;
    protected final PanelSlot slot;

    protected SelectorLabelBar labelBar;

    private final boolean bound;
    private final String titleKey;
    private List<ColumnSpec> specs;
    private GaugePartColumn[] columns;

    private boolean inverted;

    private int style;

    private IconButton invertButton;

    private final List<IconButton> optionButtons = new ArrayList<>();

    private int options;

    private Rect2i inventoryArea = new Rect2i(0, 0, 0, 0);

    protected int guiLeft;
    protected int guiTop;

    protected PartSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, String titleKey) {
        super(menu, inventory, Component.translatable("gui.loconautics." + titleKey));
        this.bound = menu.part() != null;
        this.panelPos = bound ? menu.panelPos() : BlockPos.ZERO;
        this.slot = bound ? menu.slot() : PanelSlot.values()[0];
        this.titleKey = titleKey;
    }

    protected abstract PartModelViewer viewer();

    protected BlockState state() {
        return ControlsBlocks.LOCOMOTIVE_GAUGE.get().defaultBlockState();
    }

    protected abstract List<ColumnSpec> columnSpecs();

    protected abstract int initialSelection(int column);

    protected abstract void pushToViewer();

    protected abstract void sendApply();

    protected boolean supportsInvert() {
        return false;
    }

    @Nullable
    protected PanelStyleSet styleSet() {
        return null;
    }

    protected int initialStyle() {
        return 0;
    }

    protected final int selectedStyle() {
        return style;
    }

    protected int initialOptions() {
        return 0;
    }

    protected final int selectedOptions() {
        PanelStyleSet set = styleSet();
        return set == null ? options : set.effectiveOptions(options, style);
    }

    protected final int rawOptions() {
        return options;
    }

    protected final int selected(int column) {
        return columns[column].selected();
    }

    protected void addExtraControls() {
    }

    protected int optionX(PanelStyleSet set) {
        return SelectorPanel.OPTION_X;
    }

    protected boolean hasMetricBar() {
        return false;
    }

    protected final void setSelected(int column, int value) {
        if (columns != null) columns[column].setSelected(value);
    }

    @Override
    protected void init() {
        setWindowSize(PanelPartLayout.WINDOW_W, PanelPartLayout.INV_Y + 108);
        super.init();
        menu.slotsActive = true;
        optionButtons.clear();
        guiLeft = leftPos;
        guiTop = topPos;
        inventoryArea = new Rect2i(leftPos + PanelPartLayout.INV_X, topPos + PanelPartLayout.INV_Y, 176, 108);

        viewer().setState(state());
        style = initialStyle();
        options = initialOptions();

        specs = columnSpecs();
        columns = new GaugePartColumn[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            ColumnSpec spec = specs.get(i);
            columns[i] = SelectorPanel.column(guiLeft, guiTop, spec.stripX(), spec.count(), spec.renderer());
            columns[i].setSelected(initialSelection(i));
        }

        IconButton confirm = new SelectorSpriteButton(guiLeft + SelectorPanel.CONFIRM_X,
                guiTop + SelectorPanel.CONFIRM_Y, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        confirm.setToolTip(Component.translatable("gui.loconautics." + titleKey + ".done"));
        addRenderableWidget(confirm);

        PanelStyleSet set = styleSet();
        if (set != null) {
            addStyleControls(set);
        }

        labelBar = new SelectorLabelBar(font, this::setFocused, panelPos, slot, guiLeft, guiTop);
        addRenderableWidget(labelBar.createBox());
        addExtraControls();

        if (supportsInvert()) {
            inverted = Minecraft.getInstance().level != null
                    && Minecraft.getInstance().level.getBlockEntity(panelPos) instanceof PanelBlockEntity panel
                    && panel.isInverted(slot);
            invertButton = new SelectorSpriteButton(guiLeft + SelectorPanel.INVERT_X, guiTop + SelectorPanel.INVERT_Y,
                    AllIcons.I_FLIP);
            invertButton.withCallback(this::toggleInvert);
            addRenderableWidget(invertButton);
            refreshInvertButton();
        }
    }

    private void addStyleControls(PanelStyleSet set) {
        if (set.count() > 1) {
            IconButton previous = new SelectorArrowButton(guiLeft + SelectorPanel.STYLE_PREV_X,
                    guiTop + SelectorPanel.STYLE_ARROW_Y, false);
            previous.withCallback(() -> stepStyle(-1));
            previous.setToolTip(Component.translatable("gui.loconautics.selector.style.previous"));
            addRenderableWidget(previous);
            IconButton next = new SelectorArrowButton(guiLeft + SelectorPanel.STYLE_NEXT_X,
                    guiTop + SelectorPanel.STYLE_ARROW_Y, true);
            next.withCallback(() -> stepStyle(1));
            next.setToolTip(Component.translatable("gui.loconautics.selector.style.next"));
            addRenderableWidget(next);
        }
        SelectorOptionButtons.add(set, guiLeft + optionX(set), guiTop + SelectorPanel.OPTION_Y,
                () -> options, next -> options = next, button -> {
                    optionButtons.add(button);
                    addRenderableWidget(button);
                });
    }

    private void stepStyle(int step) {
        style = Math.floorMod(style + step, styleSet().count());
    }

    private void toggleInvert() {
        inverted = !inverted;
        PacketDistributor.sendToServer(new PanelInvertPacket(panelPos, slot.ordinal(), inverted));
        refreshInvertButton();
    }

    private void refreshInvertButton() {
        invertButton.green = inverted;
    }

    private void renderStyleDots(GuiGraphics graphics, int mouseX, int mouseY) {
        PanelStyleSet set = styleSet();
        if (set == null || set.count() < 2) return;
        int hovered = SelectorPanel.styleDotAt(guiLeft, guiTop, set.count(), mouseX, mouseY);
        SelectorPanel.renderStyleDots(graphics, guiLeft, guiTop, set.count(), style, hovered);
        if (hovered < 0) return;
        graphics.renderComponentTooltip(font, List.of(
                Component.translatable("gui.loconautics.selector.style"),
                Component.translatable(set.langKey(hovered)).withStyle(ChatFormatting.GRAY)),
                mouseX, mouseY);
    }

    private int styleDotUnder(double mouseX, double mouseY) {
        PanelStyleSet set = styleSet();
        if (set == null || set.count() < 2) return -1;
        return SelectorPanel.styleDotAt(guiLeft, guiTop, set.count(), mouseX, mouseY);
    }

    private void renderInvertTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (invertButton == null || !invertButton.isMouseOver(mouseX, mouseY)) return;
        graphics.renderComponentTooltip(font, List.of(
                Component.translatable("gui.loconautics.selector.invert"),
                Component.translatable(inverted
                                ? "gui.loconautics.selector.invert.on" : "gui.loconautics.selector.invert.off")
                        .withStyle(inverted ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY)),
                mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        SelectorPanel.blitPanel(graphics, guiLeft, guiTop);
        if (menu.hasFrequency()) {
            SelectorPanel.blitSignalRow(graphics, guiLeft, guiTop);
            Component signal = menu.rowLabels[0];
            graphics.drawString(font, signal,
                    guiLeft + SelectorPanel.SIGNAL_TEXT_X + (SelectorPanel.SIGNAL_TEXT_W - font.width(signal)) / 2,
                    guiTop + SelectorPanel.SIGNAL_TEXT_Y, SelectorPanel.SIGNAL_TEXT_COLOR, true);
        }
        if (hasMetricBar()) SelectorPanel.blitMetricRow(graphics, guiLeft, guiTop);
        for (ColumnSpec spec : specs) {
            SelectorPanel.blitStrip(graphics, guiLeft, guiTop, spec.stripX());
        }

        pushToViewer();
        SelectorPanel.renderViewer(graphics, viewer(), guiLeft, guiTop);
        for (GaugePartColumn column : columns) {
            column.render(graphics);
        }
        renderPlayerInventory(graphics, guiLeft + PanelPartLayout.INV_X, guiTop + PanelPartLayout.INV_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PanelStyleSet set = styleSet();
        boolean optionsShown = set == null || set.hasOptions(style);
        optionButtons.forEach(button -> button.visible = optionsShown);
        super.render(graphics, mouseX, mouseY, partialTick);
        SelectorPanel.renderViewerOverlay(graphics, font, viewer(), guiLeft, guiTop);
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, SelectorPanel.OVERLAY_Z_ABOVE_PREVIEW_MODEL);
        renderStyleDots(graphics, mouseX, mouseY);
        for (int i = 0; i < columns.length; i++) {
            SelectorPanel.renderColumnTooltip(graphics, font, columns[i], specs.get(i).tooltipKey(), mouseX, mouseY);
        }
        renderInvertTooltip(graphics, mouseX, mouseY);
        labelBar.render(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(inventoryArea);
    }

    @Override
    public void onClose() {
        if (bound && columns != null) {
            sendApply();
        }
        if (labelBar != null) labelBar.save();
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (labelBar.keyPressed(keyCode)) return true;
        if (labelBar.typing(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (labelBar.mouseClicked(mouseX, mouseY, button)) return true;
        if (SelectorPanel.clickedReset(viewer(), guiLeft, guiTop, mouseX, mouseY, button)) return true;
        int dot = styleDotUnder(mouseX, mouseY);
        if (dot >= 0 && button == 0) {
            style = dot;
            return true;
        }
        if (viewer().mouseClicked(mouseX, mouseY, button)) return true;
        for (GaugePartColumn column : columns) {
            if (column.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (viewer().mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (viewer().mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (styleDotUnder(mouseX, mouseY) >= 0 && scrollY != 0) {
            style = Math.floorMod(style + (scrollY > 0 ? -1 : 1), styleSet().count());
            return true;
        }
        if (viewer().mouseScrolled(mouseX, mouseY, scrollY)) return true;
        for (GaugePartColumn column : columns) {
            if (column.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
