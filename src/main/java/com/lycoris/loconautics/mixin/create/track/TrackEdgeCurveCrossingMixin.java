package com.lycoris.loconautics.mixin.create.track;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import java.util.ArrayList;
import java.util.Collection;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrackEdge.class, remap = false)
public abstract class TrackEdgeCurveCrossingMixin {

    @Inject(method = "getIntersection", at = @At("HEAD"), cancellable = true)
    private void loconautics$samplePointsOnce(TrackNode node1, TrackNode node2, TrackEdge other, TrackNode other1,
                                              TrackNode other2, CallbackInfoReturnable<Collection<double[]>> cir) {
        TrackEdge self = (TrackEdge) (Object) this;
        if (!self.isTurn() || !other.isTurn() || self.isInterDimensional() || other.isInterDimensional()) {
            return;
        }
        Vec3 v1 = node1.getLocation().getLocation();
        Vec3 v2 = node2.getLocation().getLocation();
        Vec3 w1 = other1.getLocation().getLocation();
        Vec3 w2 = other2.getLocation().getLocation();
        if (v1.y != v2.y || v1.y != w1.y || v1.y != w2.y) {
            return;
        }
        if (!self.getTurn().getBounds().intersects(other.getTurn().getBounds())) {
            return;
        }

        int otherCount = other.getTurn().getSegmentCount();
        double otherLength = other.getLength();
        Vec3[] otherPoints = new Vec3[otherCount + 1];
        otherPoints[0] = w1;
        for (int j = 1; j <= otherCount; j++) {
            otherPoints[j] = other.getPosition(null, j * .5 / otherLength);
        }

        double length = self.getLength();
        Collection<double[]> intersections = new ArrayList<>();
        Vec3 seg1 = v1;
        double t = 0;
        for (int i = 0; i < self.getTurn().getSegmentCount(); i++) {
            double tOffset = t;
            t += .5;
            Vec3 seg2 = self.getPosition(null, t / length);
            for (int j = 0; j < otherCount; j++) {
                double[] intersection = VecHelper.intersectRanged(seg1, otherPoints[j], seg2, otherPoints[j + 1], Axis.Y);
                if (intersection == null) {
                    continue;
                }
                intersection[0] += tOffset;
                intersection[1] += j * .5;
                intersections.add(intersection);
            }
            seg1 = seg2;
        }
        cir.setReturnValue(intersections);
    }
}
