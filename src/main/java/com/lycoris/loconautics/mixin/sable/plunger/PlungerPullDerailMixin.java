package com.lycoris.loconautics.mixin.sable.plunger;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LaunchedPlungerEntity.class, remap = false)
public abstract class PlungerPullDerailMixin {

    @WrapOperation(method = "physicsTick", at = @At(value = "INVOKE", ordinal = 0,
            target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyForcesAndReset(Ldev/ryanhcode/sable/api/physics/force/ForceTotal;)V"))
    private void loconautics$pullSelf(RigidBodyHandle handle, ForceTotal forces, Operation<Void> original,
                                      @Local(argsOnly = true) ServerSubLevel subLevel,
                                      @Local(argsOnly = true) double timeStep) {
        ExternalForceDerail.notePlunger(subLevel, new Vector3d(forces.getLocalForce()), timeStep);
        original.call(handle, forces);
    }

    @WrapOperation(method = "physicsTick", at = @At(value = "INVOKE", ordinal = 1,
            target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyForcesAndReset(Ldev/ryanhcode/sable/api/physics/force/ForceTotal;)V"))
    private void loconautics$pullOther(RigidBodyHandle handle, ForceTotal forces, Operation<Void> original,
                                       @Local(ordinal = 1) ServerSubLevel otherSubLevel,
                                       @Local(argsOnly = true) double timeStep) {
        ExternalForceDerail.notePlunger(otherSubLevel, new Vector3d(forces.getLocalForce()), timeStep);
        original.call(handle, forces);
    }
}
