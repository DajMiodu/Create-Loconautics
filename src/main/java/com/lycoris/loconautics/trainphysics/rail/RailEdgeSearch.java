package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RailEdgeSearch {

    private static final double SAMPLE_STEP = 0.25;

    private RailEdgeSearch() {
    }

    public record Found(TrackGraphLocation location, Vec3 upNormal, double railY, double distSqr) {}

    // a bend has track blocks only at its ends, so the middle is found by walking graph edges in the hosts frame
    public static Found nearest(ResourceKey<Level> dimension, ServerSubLevel host, Vec3 origin, Vec3 look,
                                double radius, double below, boolean hanging) {
        double minY = hanging ? origin.y : origin.y - below;
        double maxY = hanging ? origin.y + below : origin.y;
        double lookFlat = Math.sqrt(look.x * look.x + look.z * look.z);
        Found best = null;
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (TrackNodeLocation nodeLocation : graph.getNodes()) {
                if (!dimension.equals(nodeLocation.dimension)) {
                    continue;
                }
                TrackNode node1 = graph.locateNode(nodeLocation);
                if (node1 == null) {
                    continue;
                }
                Vec3 start = nodeLocation.getLocation();
                for (var entry : graph.getConnectionsFrom(node1).entrySet()) {
                    TrackEdge edge = entry.getValue();
                    double length = edge.getLength();
                    if (edge.isInterDimensional() || length <= 0.0) {
                        continue;
                    }
                    if (horizontalSqr(start, origin) > (length + radius) * (length + radius)) {
                        continue;
                    }
                    TrackNodeLocation endLocation = entry.getKey().getLocation();
                    if (RailSeams.serverHostOf(nodeLocation) != host || RailSeams.serverHostOf(endLocation) != host) {
                        continue;
                    }
                    Found found = nearestOnEdge(graph, edge, nodeLocation, endLocation, length, origin, look,
                            lookFlat, radius, minY, maxY);
                    if (found != null && (best == null || found.distSqr < best.distSqr)) {
                        best = found;
                    }
                }
            }
        }
        return best;
    }

    private static Found nearestOnEdge(TrackGraph graph, TrackEdge edge, TrackNodeLocation first,
                                       TrackNodeLocation second, double length, Vec3 origin, Vec3 look,
                                       double lookFlat, double radius, double minY, double maxY) {
        int steps = Math.max(1, (int) Math.ceil(length / SAMPLE_STEP));
        double bestT = -1.0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = edge.getPosition(graph, t);
            if (p.y < minY || p.y > maxY + 1.0 || horizontalSqr(p, origin) > radius * radius) {
                continue;
            }
            double dist = p.distanceToSqr(origin);
            if (dist < bestDist) {
                bestDist = dist;
                bestT = t;
            }
        }
        if (bestT < 0.0) {
            return null;
        }
        if (lookFlat > 1.0e-6) {
            double ahead = Math.min(1.0, bestT + 0.5 / length);
            double behind = Math.max(0.0, bestT - 0.5 / length);
            Vec3 tangent = edge.getPosition(graph, ahead).subtract(edge.getPosition(graph, behind));
            // the reverse edge covers the same point, keep only the one pointing the way the car looks
            if (tangent.x * look.x + tangent.z * look.z < 0.0) {
                return null;
            }
        }
        TrackGraphLocation location = new TrackGraphLocation();
        location.graph = graph;
        location.edge = Couple.create(first, second);
        location.position = bestT * length;
        Vec3 point = edge.getPosition(graph, bestT);
        return new Found(location, edge.getNormal(graph, bestT), point.y, bestDist);
    }

    private static double horizontalSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
