package com.lycoris.loconautics.mixin.client.physicsstaff;

import com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(value = PhysicsStaffClientHandler.class, remap = false)
public abstract class PhysicsStaffLockMixin {

    @Inject(method = "setLocks", at = @At("RETURN"))
    private void loconautics$mirrorStaffLocks(ResourceKey<Level> dimension, List<UUID> locks, CallbackInfo ci) {
        BogeyWheelAnimator.setStaffLocks(dimension, locks);
    }
}
