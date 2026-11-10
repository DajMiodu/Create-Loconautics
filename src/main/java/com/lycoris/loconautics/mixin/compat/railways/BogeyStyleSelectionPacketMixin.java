package com.lycoris.loconautics.mixin.compat.railways;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.railwayteam.railways.util.packet.BogeyStyleSelectionPacket", remap = false)
public abstract class BogeyStyleSelectionPacketMixin {

    @Inject(method = "<init>(Lcom/simibubi/create/content/trains/bogey/BogeyStyle;"
            + "Lcom/simibubi/create/content/trains/bogey/BogeySizes$BogeySize;)V", at = @At("RETURN"))
    private void loconautics$rememberPick(BogeyStyle style, BogeySizes.BogeySize size, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            BogeyMenuHandlerServerAccessor.loconautics$addStyle(player.getUUID(), Pair.of(style, size));
        }
    }
}
