package com.lycoris.loconautics.mixin.sable.physicsstaff;

import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import dev.simulated_team.simulated.network.packets.physics_staff.PhysicsStaffActionPacket;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PhysicsStaffActionPacket.class, remap = false)
public abstract class PhysicsStaffStopDragMixin {

    @Shadow
    @Final
    protected PhysicsStaffAction action;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void loconautics$stopDragWithoutKick(PacketContext context, CallbackInfo ci) {
        if (action != PhysicsStaffAction.STOP_DRAG) {
            return;
        }
        Player player = context.player();
        if (player == null || PhysicsStaffItem.isHolding(player)) {
            return;
        }
        if (context.level() instanceof ServerLevel level) {
            PhysicsStaffServerHandler.get(level).stopDragging(player.getUUID());
        }
        ci.cancel();
    }
}
