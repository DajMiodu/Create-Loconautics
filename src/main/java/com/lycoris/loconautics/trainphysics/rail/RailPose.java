package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class RailPose {

    public static Vector3d position(TravellingPoint point, TrackGraph graph) {
        if (point == null || point.edge == null) {
            return null;
        }
        Vec3 p = point.getPosition(graph);
        return new Vector3d(p.x, p.y, p.z);
    }

    public static Vector3d carriagePosition(TravellingPoint leading, TravellingPoint trailing, TrackGraph graph) {
        Vector3d a = position(leading, graph);
        Vector3d b = position(trailing, graph);
        if (a == null || b == null) {
            return null;
        }
        return a.add(b).mul(0.5);
    }

    // orientation is intentionally not provided here, a raw euler yawpitch construction introduced a roll bug on curves railcarriage derives it from a reference frame matrix instead

    private RailPose() {
    }
}
