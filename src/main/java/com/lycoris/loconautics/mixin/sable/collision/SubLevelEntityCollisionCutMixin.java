package com.lycoris.loconautics.mixin.sable.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.mixin.sable.LevelAcceleratorLevelAccessor;
import com.lycoris.loconautics.trainphysics.portal.PortalCutLookup;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SubLevelEntityCollisionCutMixin {

    @WrapOperation(
            method = {"collide", "hasCollision"},
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/util/LevelAccelerator;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState loconautics$skipCutBlocks(LevelAccelerator accel, BlockPos pos,
                                                       Operation<BlockState> original) {
        if (PortalCutLookup.hidden(((LevelAcceleratorLevelAccessor) accel).loconautics$level(), pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return original.call(accel, pos);
    }
}
