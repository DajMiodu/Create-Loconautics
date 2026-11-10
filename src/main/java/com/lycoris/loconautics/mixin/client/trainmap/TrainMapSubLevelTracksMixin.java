package com.lycoris.loconautics.mixin.client.trainmap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.client.trainmap.SubLevelTrackPainter;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.compat.trainmap.TrainMapRenderer;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// create draws every node at its plot coordinates, so sub level rails are skipped there and painted in world space
@Mixin(value = TrainMapManager.class, remap = false)
public abstract class TrainMapSubLevelTracksMixin {

    @Shadow
    public static void redrawAll(ResourceKey<Level> dimension) {
        throw new AssertionError();
    }

    @Inject(method = "tick(Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"))
    private static void loconautics$redrawWhenBodiesMove(ResourceKey<Level> dimension, CallbackInfo ci) {
        if (SubLevelTrackPainter.needsRedraw()) {
            redrawAll(dimension);
        }
    }

    @Inject(method = "redrawAll", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/compat/trainmap/TrainMapRenderer;finishDrawing()V"))
    private static void loconautics$paintSubLevelTracks(ResourceKey<Level> dimension, CallbackInfo ci) {
        SubLevelTrackPainter.paint(TrainMapRenderer.INSTANCE, dimension);
    }

    @WrapOperation(method = "renderPhase", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/graph/TrackGraph;getNodes()Ljava/util/Set;"))
    private static Set<TrackNodeLocation> loconautics$skipSubLevelNodes(TrackGraph graph,
                                                                        Operation<Set<TrackNodeLocation>> original) {
        Set<TrackNodeLocation> kept = new HashSet<>();
        for (TrackNodeLocation location : original.call(graph)) {
            if (!RailSeams.inPlotGrid(location)) {
                kept.add(location);
            }
        }
        return kept;
    }

    @WrapOperation(method = "renderPhase", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/graph/TrackGraph;getConnectionsFrom("
                    + "Lcom/simibubi/create/content/trains/graph/TrackNode;)Ljava/util/Map;"))
    private static Map<TrackNode, TrackEdge> loconautics$skipSeamEdges(TrackGraph graph, TrackNode node,
                                                                       Operation<Map<TrackNode, TrackEdge>> original) {
        Map<TrackNode, TrackEdge> connections = original.call(graph, node);
        if (connections == null) {
            return null;
        }
        Map<TrackNode, TrackEdge> kept = new HashMap<>();
        for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
            if (!RailSeams.inPlotGrid(entry.getKey().getLocation())) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        return kept.size() == connections.size() ? connections : kept;
    }
}
