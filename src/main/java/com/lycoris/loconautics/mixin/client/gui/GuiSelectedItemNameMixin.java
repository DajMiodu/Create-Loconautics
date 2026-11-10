package com.lycoris.loconautics.mixin.client.gui;

import com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiSelectedItemNameMixin {

    @Shadow
    private int toolHighlightTimer;

    @Inject(method = "render", at = @At("HEAD"))
    private void loconautics$keepItemName(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ScrollMenuHandler.isOpen()) {
            this.toolHighlightTimer = 40;
        }
    }
}
