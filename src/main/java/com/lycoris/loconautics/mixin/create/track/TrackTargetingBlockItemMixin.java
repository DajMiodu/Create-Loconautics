package com.lycoris.loconautics.mixin.create.track;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.common.sable.SubLevelFrames;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TrackTargetingBlockItem.class, remap = false)
public abstract class TrackTargetingBlockItemMixin {

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"))
    private boolean loconautics$reachInWorld(BlockPos selected, Vec3i placed, double distance,
                                             Operation<Boolean> original, UseOnContext context) {
        return SubLevelFrames.closerInWorld(context.getLevel(), selected, placed, distance);
    }

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/track/ITrackBlock;getNearestTrackAxis(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)Lnet/createmod/catnip/data/Pair;"))
    private Pair<Vec3, AxisDirection> loconautics$localLook(ITrackBlock track, BlockGetter getter, BlockPos pos,
                                                            BlockState state, Vec3 look,
                                                            Operation<Pair<Vec3, AxisDirection>> original) {
        return original.call(track, getter, pos, state, SubLevelFrames.lookToLocal(getter, pos, look));
    }
}
