package com.lycoris.loconautics.mixin.sable.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.common.turntable.PlateGraceCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery", remap = false)
public abstract class PlateGraceBakeMixin {

    // every state gets the grace wrapper, it only acts when the contact belongs to a turntable plate
    @WrapOperation(method = "buildPhysicsDataForBlock", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/block/BlockWithSubLevelCollisionCallback;sable$getCallback(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;"))
    private BlockSubLevelCollisionCallback loconautics$wrapPlateGrace(BlockState state,
            Operation<BlockSubLevelCollisionCallback> original) {
        return new PlateGraceCallback(original.call(state));
    }
}
