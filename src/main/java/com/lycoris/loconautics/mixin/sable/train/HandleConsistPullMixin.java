package com.lycoris.loconautics.mixin.sable.train;

import com.lycoris.loconautics.trainphysics.TrainConsist;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity$HandleConstraint", remap = false)
public abstract class HandleConsistPullMixin {

    @Shadow
    @Final
    private UUID playerId;

    @Shadow
    public abstract void removeJoint();

    @Inject(method = "physicsTick", at = @At("HEAD"), cancellable = true)
    private void loconautics$noPullOnOwnConsist(ServerSubLevel subLevel, RigidBodyHandle handle, CallbackInfo ci) {
        if (!(subLevel.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = level.getPlayerByUUID(playerId);
        if (player != null && TrainConsist.standingOnConsistOf(level, player, subLevel.getUniqueId())) {
            removeJoint();
            ci.cancel();
        }
    }
}
