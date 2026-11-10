package com.lycoris.loconautics.mixin.create.link;

import com.lycoris.loconautics.content.controls.panel.PanelLink;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LinkBehaviour.class, remap = false)
public abstract class LinkBehaviourPanelMixin extends BlockEntityBehaviour {

    private LinkBehaviourPanelMixin() {
        super(null);
    }

    private boolean loconautics$panelDriven() {
        return blockEntity != null && PanelLink.isPanel(blockEntity.getBlockState());
    }

    @Inject(method = "testHit", at = @At("HEAD"), cancellable = true)
    private void loconautics$blockFrequencySlots(Boolean first, Vec3 hit, CallbackInfoReturnable<Boolean> cir) {
        if (loconautics$panelDriven()) cir.setReturnValue(false);
    }

    @Inject(method = "setFrequency", at = @At("HEAD"), cancellable = true)
    private void loconautics$lockFrequency(boolean first, ItemStack stack, CallbackInfo ci) {
        if (loconautics$panelDriven()) ci.cancel();
    }

    @Inject(method = "getTransmittedStrength", at = @At("HEAD"), cancellable = true)
    private void loconautics$muteTransmission(CallbackInfoReturnable<Integer> cir) {
        if (loconautics$panelDriven()) cir.setReturnValue(0);
    }

    @Inject(method = "setReceivedStrength", at = @At("HEAD"), cancellable = true)
    private void loconautics$muteReception(int networkPower, CallbackInfo ci) {
        if (loconautics$panelDriven()) ci.cancel();
    }
}
