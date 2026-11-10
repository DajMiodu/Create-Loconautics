package com.lycoris.loconautics.client.gui.selector;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;

public class SelectorArrowButton extends IconButton {

    private final boolean next;

    public SelectorArrowButton(int x, int y, boolean next) {
        super(x, y, SelectorPanel.ARROW_SIZE, SelectorPanel.ARROW_SIZE, AllIcons.I_CONFIG_PREV);
        this.next = next;
    }

    @Override
    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
        SelectorPanel.blitArrow(graphics, getX(), getY(), next, isHovered);
    }
}
