package com.lycoris.loconautics.mixin.client.ponder;

import com.lycoris.loconautics.client.ponder.DeferredGui;
import com.lycoris.loconautics.client.ponder.GuiPonder;
import net.createmod.ponder.api.element.AnimatedOverlayElement;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
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

    @Inject(method = "renderSceneOverlay", at = @At("HEAD"))
    private void loconautics$drawGuisUnderText(GuiGraphics graphics, float partialTicks, float lazyIndexValue,
                                               float indexDiff, CallbackInfo ci) {
        PonderUI ui = (PonderUI) (Object) this;
        PonderScene scene = ui.getActiveScene();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        scene.forEachVisible(PonderOverlayElement.class, element -> {
            if (element instanceof DeferredGui gui && element instanceof AnimatedOverlayElement animated) {
                gui.drawDeferred(scene, ui, graphics, partialTicks, animated.getFade(partialTicks));
            }
        });
        graphics.pose().popPose();
    }
}
