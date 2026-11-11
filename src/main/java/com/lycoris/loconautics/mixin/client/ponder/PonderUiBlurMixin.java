package com.lycoris.loconautics.mixin.client.ponder;

import com.lycoris.loconautics.client.ponder.GuiPonder;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PonderUI.class)
public abstract class PonderUiBlurMixin {

    @Inject(method = "renderVisibleScenes", at = @At("RETURN"))
    private void loconautics$blurScene(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!GuiPonder.blurring()) {
            return;
        }
        graphics.flush();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.processBlurEffect(partialTicks);
        minecraft.getMainRenderTarget().bindWrite(false);
    }
}
