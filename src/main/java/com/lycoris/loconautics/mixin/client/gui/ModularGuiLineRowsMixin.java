package com.lycoris.loconautics.mixin.client.gui;

import com.simibubi.create.foundation.gui.ModularGuiLine;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ModularGuiLine.class)
public abstract class ModularGuiLineRowsMixin {

    @Unique
    private static final int LOCONAUTICS_ROW_HEIGHT = 20;

    @Shadow
    List<Pair<AbstractWidget, String>> widgets;

    @Shadow
    List<Couple<Integer>> customBoxes;

    @Shadow
    boolean speechBubble;

    @Shadow
    protected abstract void box(GuiGraphics graphics, int x, int width, boolean b);

    @Inject(method = "renderWidgetBG", at = @At("HEAD"), cancellable = true)
    private void loconautics$drawEachRow(int guiLeft, GuiGraphics graphics, CallbackInfo ci) {
        if (!customBoxes.isEmpty()) {
            return;
        }
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Pair<AbstractWidget, String> pair : widgets) {
            if (pair.getSecond().equals("Dummy")) {
                continue;
            }
            top = Math.min(top, pair.getFirst().getY());
            bottom = Math.max(bottom, pair.getFirst().getY());
        }
        if (bottom - top < LOCONAUTICS_ROW_HEIGHT / 2) {
            return;
        }

        boolean first = true;
        for (Pair<AbstractWidget, String> pair : widgets) {
            if (pair.getSecond().equals("Dummy")) {
                continue;
            }
            AbstractWidget widget = pair.getFirst();
            int x = widget.getX();
            int width = widget.getWidth();
            if (widget instanceof EditBox) {
                x -= 5;
                width += 9;
            }
            int row = Math.round((widget.getY() - top) / (float) LOCONAUTICS_ROW_HEIGHT);
            graphics.pose().pushPose();
            graphics.pose().translate(0, row * LOCONAUTICS_ROW_HEIGHT, 0);
            box(graphics, x, width, first & speechBubble);
            graphics.pose().popPose();
            first = false;
        }
        ci.cancel();
    }
}
