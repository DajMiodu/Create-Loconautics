package com.lycoris.loconautics.content.schedule.map.client;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MapTrackOverlay {

    private MapTrackOverlay() {}

    public record Edge(UUID graphId, TrackNodeLocation n1, TrackNodeLocation n2,
                       int netMin, int netMax, double[] xs, double[] zs) {

        public double arcLength() {
            double total = 0.0;
            for (int i = 1; i < xs.length; i++) {
                double dx = xs[i] - xs[i - 1];
                double dz = zs[i] - zs[i - 1];
                total += Math.sqrt(dx * dx + dz * dz);
            }
            return total;
        }

        public long key() {
            return ((long) netMin << 32) | (netMax & 0xFFFFFFFFL);
        }
    }

    public static List<Edge> collect(ResourceKey<Level> dim) {
        List<Edge> out = new ArrayList<>();
        if (Create.RAILWAYS.trackNetworks == null) return out;

        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (TrackNodeLocation locA : graph.getNodes()) {
                if (!dim.equals(locA.getDimension())) continue;
                TrackNode a = graph.locateNode(locA);
                if (a == null) continue;

                for (Map.Entry<TrackNode, TrackEdge> entry : graph.getConnectionsFrom(a).entrySet()) {
                    TrackNode b = entry.getKey();
                    TrackEdge edge = entry.getValue();
                    if (edge.isInterDimensional()) continue;
                    if (a.getNetId() > b.getNetId()) continue;

                    int seg = edge.isTurn() ? Math.max(1, edge.getTurn().getSegmentCount()) : 1;
                    double[] xs = new double[seg + 1];
                    double[] zs = new double[seg + 1];
                    for (int i = 0; i <= seg; i++) {
                        Vec3 p = edge.getPosition(graph, (double) i / seg);
                        xs[i] = p.x;
                        zs[i] = p.z;
                    }
                    out.add(new Edge(graph.id, a.getLocation(), b.getLocation(),
                            Math.min(a.getNetId(), b.getNetId()),
                            Math.max(a.getNetId(), b.getNetId()), xs, zs));
                }
            }
        }
        return out;
    }
}
