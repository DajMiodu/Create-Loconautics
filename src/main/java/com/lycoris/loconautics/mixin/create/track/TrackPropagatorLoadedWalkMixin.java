package com.lycoris.loconautics.mixin.create.track;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.content.locomotiveready.CheckerFlatGenerator;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.TrackPropagator;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TrackPropagator.class, remap = false)
public abstract class TrackPropagatorLoadedWalkMixin {

    @WrapOperation(method = "onRailAdded", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/track/ITrackBlock;walkConnectedTracks(Lnet/minecraft/world/level/BlockGetter;Lcom/simibubi/create/content/trains/graph/TrackNodeLocation;Z)Ljava/util/Collection;"))
    private static Collection<TrackNodeLocation.DiscoveredLocation> loconautics$stopAtUnloadedChunks(
            BlockGetter world, TrackNodeLocation location, boolean linear,
            Operation<Collection<TrackNodeLocation.DiscoveredLocation>> original) {
        if (world instanceof ServerLevel level
                && level.getChunkSource().getGenerator() instanceof CheckerFlatGenerator) {
            for (BlockPos pos : location.allAdjacent()) {
                if (!level.hasChunkAt(pos)) {
                    return new ArrayList<>();
                }
            }
        }
        return original.call(world, location, linear);
    }
}
