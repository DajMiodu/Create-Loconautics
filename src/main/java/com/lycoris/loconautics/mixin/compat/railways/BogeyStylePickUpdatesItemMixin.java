package com.lycoris.loconautics.mixin.compat.railways;

import com.lycoris.loconautics.compat.railways.RailwaysBogeyChoice;
import com.lycoris.loconautics.content.casingpicker.BogeyItem;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.railwayteam.railways.util.packet.BogeyStyleSelectionPacket", remap = false)
public abstract class BogeyStylePickUpdatesItemMixin {

    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void loconautics$restyleHeldBogey(ServerPlayer sender, CallbackInfo ci) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = sender.getItemInHand(hand);
            if (held.getItem() instanceof BogeyItem) {
                held.set(LoconauticsRegistries.BOGEY_CHOICE.get(), RailwaysBogeyChoice.selected(sender.getUUID()));
            }
        }
    }
}
