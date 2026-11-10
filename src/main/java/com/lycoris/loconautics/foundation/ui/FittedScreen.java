package com.lycoris.loconautics.foundation.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public abstract class FittedScreen extends NoBlurScreen {

    private static final float MARGIN = 4f;
    private static final float MIN_FIT = 0.3f;

    private boolean insideFit;

    protected FittedScreen(Component title) {
        super(title);
    }

    protected abstract int fitWidth();

    protected abstract int fitHeight();

    protected final float fit() {
        float byWidth = (width - MARGIN * 2f) / fitWidth();
        float byHeight = (height - MARGIN * 2f) / fitHeight();
        return Math.max(MIN_FIT, Math.min(1f, Math.min(byWidth, byHeight)));
    }

    private double localX(double x) {
        return (x - width / 2.0) / fit() + width / 2.0;
    }

    private double localY(double y) {
        return (y - height / 2.0) / fit() + height / 2.0;
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float fit = fit();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        insideFit = true;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, height / 2f, 0f);
        graphics.pose().scale(fit, fit, 1f);
        graphics.pose().translate(-width / 2f, -height / 2f, 0f);
        renderFitted(graphics, (int) Math.round(localX(mouseX)), (int) Math.round(localY(mouseY)), partialTick);
        graphics.pose().popPose();
        insideFit = false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (insideFit) return;
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    protected void renderFitted(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return fittedMouseClicked(localX(mouseX), localY(mouseY), button);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return fittedMouseReleased(localX(mouseX), localY(mouseY), button);
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float fit = fit();
        return fittedMouseDragged(localX(mouseX), localY(mouseY), button, dragX / fit, dragY / fit);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return fittedMouseScrolled(localX(mouseX), localY(mouseY), scrollX, scrollY);
    }

    @Override
    public final void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(localX(mouseX), localY(mouseY));
    }

    protected boolean fittedMouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected boolean fittedMouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected boolean fittedMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    protected boolean fittedMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
