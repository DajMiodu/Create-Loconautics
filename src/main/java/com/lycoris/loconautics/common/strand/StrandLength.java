package com.lycoris.loconautics.common.strand;

import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import org.joml.Vector3dc;

import java.util.List;

public final class StrandLength {

    public static final double SEGMENT_LENGTH = 1.0;

    public static final double ASSUMED_GAP = 2.0;

    public static final double TOO_LONG = -1.0;

    private StrandLength() {
    }

    public static double polyline(List<? extends Vector3dc> points) {
        double total = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            total += points.get(i).distance(points.get(i + 1));
        }
        return total;
    }

    public static double current(RopePhysicsObject rope) {
        return polyline(rope.getPoints());
    }

    public static double rest(double extensionGoal, int pointCount) {
        return extensionGoal + Math.max(0, pointCount - 2) * SEGMENT_LENGTH;
    }

    public static double spawnLength(Vector3dc anchorA, Vector3dc anchorB, double maxLength) {
        double span = anchorA.distance(anchorB);
        if (!Double.isFinite(span)) {
            return TOO_LONG;
        }
        double length = Math.max(span, ASSUMED_GAP);
        return length > maxLength ? TOO_LONG : length;
    }
}
