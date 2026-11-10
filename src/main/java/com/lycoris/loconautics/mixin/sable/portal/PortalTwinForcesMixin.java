package com.lycoris.loconautics.mixin.sable.portal;

import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the master already applies every thruster, balloon and block force, a twin applying them too would double them
@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class PortalTwinForcesMixin {

    @Inject(method = "prePhysicsTick", at = @At("HEAD"), cancellable = true)
    private void loconautics$twinSkipsBlockForces(SubLevelPhysicsSystem physicsSystem, RigidBodyHandle handle,
                                                  double timeStep, CallbackInfo ci) {
        if (PortalTwins.isTwin(((ServerSubLevel) (Object) this).getUniqueId())) {
            ci.cancel();
        }
    }

    @Inject(method = "applyQueuedForces", at = @At("HEAD"), cancellable = true)
    private void loconautics$twinSkipsQueuedForces(SubLevelPhysicsSystem physicsSystem, RigidBodyHandle handle,
                                                   double timeStep, CallbackInfo ci) {
        if (PortalTwins.isTwin(((ServerSubLevel) (Object) this).getUniqueId())) {
            ci.cancel();
        }
    }
}
