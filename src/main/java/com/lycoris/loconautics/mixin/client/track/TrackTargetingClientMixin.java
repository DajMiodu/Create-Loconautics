package com.lycoris.loconautics.mixin.client.track;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.common.sable.SubLevelFrames;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingClient;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TrackTargetingClient.class, remap = false)
public abstract class TrackTargetingClientMixin {

    @WrapOperation(method = "clientTick", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/track/ITrackBlock;getNearestTrackAxis(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)Lnet/createmod/catnip/data/Pair;"))
    private static Pair<Vec3, AxisDirection> loconautics$localLook(ITrackBlock track, BlockGetter getter,
                                                                   BlockPos pos, BlockState state, Vec3 look,
                                                                   Operation<Pair<Vec3, AxisDirection>> original) {
        Vec3 local = getter instanceof Level level ? SubLevelFrames.lookToLocal(level, pos, look) : look;
        return original.call(track, getter, pos, state, local);
    }
}
