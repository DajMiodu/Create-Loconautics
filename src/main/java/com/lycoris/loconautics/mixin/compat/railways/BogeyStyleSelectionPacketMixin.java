package com.lycoris.loconautics.mixin.compat.railways;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.createmod.catnip.data.Pair;
import com.lycoris.loconautics.client.ClientAccess;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// railways only keeps the pick on the server, a multiplayer client copies it into its own map so the casing picker can draw it
@Mixin(targets = "com.railwayteam.railways.util.packet.BogeyStyleSelectionPacket", remap = false)
public abstract class BogeyStyleSelectionPacketMixin {

    @Inject(method = "<init>(Lcom/simibubi/create/content/trains/bogey/BogeyStyle;"
            + "Lcom/simibubi/create/content/trains/bogey/BogeySizes$BogeySize;)V", at = @At("RETURN"))
    private void loconautics$rememberPick(BogeyStyle style, BogeySizes.BogeySize size, CallbackInfo ci) {
        Player player = ClientAccess.player();
        if (player != null) {
            BogeyMenuHandlerServerAccessor.loconautics$addStyle(player.getUUID(), Pair.of(style, size));
        }
    }
}
