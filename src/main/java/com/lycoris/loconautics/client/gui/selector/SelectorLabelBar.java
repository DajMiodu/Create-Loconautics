package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.LabelText;
import com.lycoris.loconautics.network.packets.controls.selector.PanelLabelPacket;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public final class SelectorLabelBar {

    private static final int ICON_SIZE = 9;

    private final Font font;
    private final Consumer<GuiEventListener> focus;
    private final BlockPos panelPos;
    private final PanelSlot slot;
    private final int left;
    private final int top;

    private EditBox box;
    private String initial = "";
    private boolean editing;

    public SelectorLabelBar(Font font, Consumer<GuiEventListener> focus, BlockPos panelPos, PanelSlot slot,
                            int left, int top) {
        this.font = font;
        this.focus = focus;
        this.panelPos = panelPos;
        this.slot = slot;
        this.left = left;
        this.top = top;
    }

    public EditBox createBox() {
        Component current = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(panelPos) instanceof PanelBlockEntity panel
                ? panel.label(slot) : null;
        initial = current == null ? "" : LabelText.toRaw(current);
        editing = false;

        box = new EditBox(new NoShadowFontWrapper(font), left, top + SelectorPanel.LABEL_Y,
                SelectorPanel.LABEL_W, 10, Component.empty());
        box.setBordered(false);
        box.setMaxLength(PanelLabelPacket.MAX_LENGTH);
        box.setTextColor(SelectorPanel.LABEL_TEXT_COLOR);
        box.setValue(initial);
        box.setResponder(raw -> center(font.width(raw)));
        box.setFocused(false);
        box.visible = false;
        center(font.width(initial));
        return box;
    }

    public boolean editing() {
        return editing;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean over = overBar(mouseX, mouseY);
        String raw = box.getValue();
        Component shown = raw.isEmpty()
                ? Component.translatable("gui.loconautics.selector.label.empty").withStyle(ChatFormatting.DARK_GRAY)
                : LabelText.parse(Component.literal(raw));
        int width = Math.min(font.width(shown), SelectorPanel.LABEL_W);
        int x = labelX(width);
        if (!editing) {
            graphics.drawString(font, shown, x, top + SelectorPanel.LABEL_Y + 1,
                    SelectorPanel.LABEL_TEXT_COLOR, false);
        }
        SelectorPanel.blitRename(graphics, Math.min(x + width + 5, left + SelectorPanel.PANEL_W - ICON_SIZE - 4),
                top + (SelectorPanel.LABEL_BAR_H - ICON_SIZE) / 2, over);
        if (!over || editing) return;
        graphics.renderComponentTooltip(font, List.of(
                Component.translatable("gui.loconautics.selector.label"),
                Component.translatable("gui.loconautics.selector.label.hint").withStyle(ChatFormatting.GRAY)),
                mouseX, mouseY);
    }

    private int labelX(int textWidth) {
        return left + SelectorPanel.PANEL_W / 2 - (Math.min(textWidth, SelectorPanel.LABEL_W) + ICON_SIZE + 5) / 2;
    }

    private void center(int textWidth) {
        box.setX(labelX(textWidth));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean onBar = overBar(mouseX, mouseY);
        if (onBar && !editing && button == 0) {
            start();
            return true;
        }
        if (!onBar && editing) stop();
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (editing) stop();
            else start();
            return true;
        }
        if (editing && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            stop();
            return true;
        }
        return false;
    }

    public boolean typing(int keyCode, int scanCode, int modifiers) {
        if (!editing || keyCode == GLFW.GLFW_KEY_ESCAPE) return false;
        box.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public void save() {
        if (box != null && !box.getValue().equals(initial)) {
            PacketDistributor.sendToServer(new PanelLabelPacket(panelPos, slot.ordinal(), box.getValue()));
        }
    }

    private boolean overBar(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + SelectorPanel.PANEL_W
                && mouseY >= top && mouseY < top + SelectorPanel.LABEL_BAR_H;
    }

    private void start() {
        editing = true;
        box.visible = true;
        box.setFocused(true);
        focus.accept(box);
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
    }

    private void stop() {
        editing = false;
        box.visible = false;
        box.setFocused(false);
        focus.accept(null);
    }
}
