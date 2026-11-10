package com.lycoris.loconautics.mixin.sable.mergingglue;

import com.lycoris.loconautics.trainphysics.spawn.ArticulationLinks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.network.packets.PlaceMergingGluePacket;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlaceMergingGluePacket.class, remap = false)
public abstract class PlaceMergingGlueHingeMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$refuseHingedGlue(ServerPacketContext ctx, CallbackInfo ci) {
        if (!(ctx.level() instanceof ServerLevel level)) {
            return;
        }
        PlaceMergingGluePacket packet = (PlaceMergingGluePacket) (Object) this;
        SubLevel parent = Sable.HELPER.getContaining(level, packet.parentPos().relative(packet.parentFacing()));
        SubLevel child = Sable.HELPER.getContaining(level, packet.childPos().relative(packet.childFacing()));
        if (parent != null && child != null && ArticulationLinks.hinged(level, parent.getUniqueId(), child.getUniqueId())) {
            ci.cancel();
        }
    }
}
