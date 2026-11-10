package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panelgauge.GaugeCompat;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GaugeCompatButton extends SelectionScrollInput {

    private static final int BLINK_MS = 450;
    private static final int STEPS_TO_HIDE_HINT = 3;
    private static final int TOOLTIP_OFFSET = 12;
    private static final int TOOLTIP_PAD = 3;
    private static final int TOOLTIP_GAP = 8;

    private final List<GaugeCompat.Entry> entries = GaugeCompat.sorted();
    private final List<Component> modLines = new ArrayList<>();
    private final String hintId;
    private int scrolls;

    public GaugeCompatButton(int x, int y, String hintId) {
        super(x, y, SelectorPanel.BUTTON_SIZE, SelectorPanel.BUTTON_SIZE);
        this.hintId = hintId;
        List<Component> names = new ArrayList<>();
        for (GaugeCompat.Entry entry : entries) {
            names.add(Component.literal(entry.name())
                    .withStyle(entry.installed() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        forOptions(names);
        titled(Component.translatable("gui.loconautics.selector.compat"));
        calling(index -> {
            if (++scrolls >= STEPS_TO_HIDE_HINT) ClientSeenHints.markSeen(hintId);
        });
    }

    @Override
    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        isHovered = isMouseOver(mouseX, mouseY);
        SelectorPanel.blitButton(graphics, getX(), getY(), isHovered, false);
        AllIcons.I_SEND_AND_RECEIVE.render(graphics, getX() + 1, getY() + 1);
        if (!ClientSeenHints.seen(hintId) && (System.currentTimeMillis() / BLINK_MS) % 2 == 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, 200f);
            graphics.drawString(Minecraft.getInstance().font, "!", getX() + width - 5, getY() - 1, 0xFFFFFF55, true);
            graphics.pose().popPose();
        }
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void updateTooltip() {
        toolTip.clear();
        if (title == null || modLines == null) return;
        modLines.clear();
        modLines.add(title.plainCopy().withStyle(style -> style.withColor(HEADER_RGB.getRGB())));
        for (int i = 0; i < options.size(); i++) {
            modLines.add(Component.empty().append(i == state ? "-> " : "> ").append(options.get(i))
                    .withStyle(i == state ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        modLines.add(Component.translatable("gui.loconautics.selector.compat.view")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static int tooltipHeight(int lines) {
        return lines * 10 + (lines > 1 ? 2 : 0) + 2 * TOOLTIP_PAD;
    }

    private void renderAt(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int top) {
        graphics.renderComponentTooltip(font, lines, mouseX, top + TOOLTIP_PAD + TOOLTIP_OFFSET);
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return;
        GaugeCompat.Entry current = entries.get(getState());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(current.name()).withStyle(style -> style.withColor(HEADER_RGB.getRGB())));
        if (!current.installed()) {
            lines.add(Component.translatable("gui.loconautics.selector.compat.missing")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        for (String block : current.blocks()) {
            lines.add(Component.literal("> " + block)
                    .withStyle(current.installed() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
        int tallest = 0;
        for (GaugeCompat.Entry entry : entries) {
            tallest = Math.max(tallest, entry.blocks().size() + (entry.installed() ? 1 : 2));
        }
        int firstHeight = tooltipHeight(modLines.size());
        int top = getY() + height / 2 - (firstHeight + TOOLTIP_GAP + tooltipHeight(tallest)) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, SelectorPanel.OVERLAY_Z_ABOVE_PREVIEW_MODEL);
        renderAt(graphics, font, modLines, mouseX, top);
        renderAt(graphics, font, lines, mouseX, top + firstHeight + TOOLTIP_GAP);
        graphics.pose().popPose();
    }
}
