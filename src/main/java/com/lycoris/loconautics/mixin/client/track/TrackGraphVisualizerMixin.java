package com.lycoris.loconautics.mixin.client.track;

import com.lycoris.loconautics.client.SignalEdgePreview;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphVisualizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrackGraphVisualizer.class, remap = false)
public abstract class TrackGraphVisualizerMixin {

    @Inject(method = "visualiseSignalEdgeGroups", at = @At("HEAD"), cancellable = true)
    private static void loconautics$inWorldSpace(TrackGraph graph, CallbackInfo ci) {
        SignalEdgePreview.show(graph);
        ci.cancel();
    }
}
