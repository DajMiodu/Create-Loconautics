package com.lycoris.loconautics.mixin.client.creativetab;

import com.lycoris.loconautics.client.gui.CreativeSectionBanners;
import com.lycoris.loconautics.client.gui.CreativeTabBanner;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CreativeModeInventoryScreen.class, remap = false)
public class CreativeTabBannerMixin {

    @Shadow
    private static CreativeModeTab selectedTab;

    @Inject(method = "render", at = @At("TAIL"))
    private void loconautics$renderBanner(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!LoconauticsRegistries.LOCONAUTICS_TAB.isBound() || selectedTab != LoconauticsRegistries.LOCONAUTICS_TAB.get()) {
            CreativeTabBanner.setPlaying(false);
            return;
        }
        CreativeTabBanner.render((CreativeModeInventoryScreen) (Object) this, graphics, mouseX, mouseY, selectedTab.getDisplayName());
        CreativeSectionBanners.render((CreativeModeInventoryScreen) (Object) this, graphics);
    }
}
