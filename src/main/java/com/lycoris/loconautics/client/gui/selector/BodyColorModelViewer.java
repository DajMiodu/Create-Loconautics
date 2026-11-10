package com.lycoris.loconautics.client.gui.selector;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BodyColorModelViewer extends PartModelViewer {

    protected int body;
    protected int color;
    protected int style;
    protected int options;

    public void setBody(int body) {
        this.body = body;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setStyle(int style) {
        this.style = style;
    }

    public void setOptions(int options) {
        this.options = options;
    }

    public void renderIconWith(GuiGraphics g, BlockState state, int body, int color, int style, int options,
                               int cx, int cy, int box) {
        setState(state);
        setBody(body);
        setColor(color);
        setStyle(style);
        setOptions(options);
        renderIcon(g, cx, cy, box);
    }
}
