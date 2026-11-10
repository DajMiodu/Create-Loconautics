package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panelvalve.ValvePartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class ValveModelViewer extends PartModelViewer {

    private static final ValveModelViewer ICON = new ValveModelViewer();

    private int wheelColor;

    public void setWheelColor(int wheelColor) {
        this.wheelColor = wheelColor;
    }

    @Override
    protected float iconDrop() {
        return 0.135f;
    }

    @Override
    protected float iconScale() {
        return 0.9f;
    }

    @Override
    protected void draw(PoseStack pose, MultiBufferSource buffers) {
        ValvePartRenderer.render(0f, pose, buffers, LightTexture.FULL_BRIGHT, state, wheelColor);
    }

    public static void renderValveIcon(GuiGraphics g, BlockState state, int wheelColor,
                                       int cx, int cy, int box) {
        ICON.setState(state);
        ICON.setWheelColor(wheelColor);
        ICON.renderIcon(g, cx, cy, box);
    }
}
