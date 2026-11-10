package com.lycoris.loconautics.client.gui.selector;

import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

public class SelectorSpriteButton extends IconButton {

    public SelectorSpriteButton(int x, int y, ScreenElement icon) {
        super(x, y, icon);
    }

    @Override
    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
        SelectorPanel.blitButton(graphics, getX(), getY(), isHovered, green);
        icon.render(graphics, getX() + 1, getY() + 1);
    }
}
