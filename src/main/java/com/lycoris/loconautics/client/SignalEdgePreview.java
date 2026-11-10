package com.lycoris.loconautics.client;

import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgeData;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.BezierConnection;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class SignalEdgePreview {

    private static final double RANGE = 50.0;
    private static final float WIDTH = 1 / 8f;

    private SignalEdgePreview() {
    }

    public static void show(TrackGraph graph) {
        Minecraft mc = Minecraft.getInstance();
        Entity cameraEntity = mc.cameraEntity;
        if (cameraEntity == null || mc.level == null) {
            return;
        }
        Vec3 camera = cameraEntity.getEyePosition();
        Map<UUID, SignalEdgeGroup> groups = Create.RAILWAYS.sided(null).signalEdgeGroups;

        for (TrackNodeLocation nodeLocation : graph.getNodes()) {
            TrackNode node = nodeLocation == null ? null : graph.locateNode(nodeLocation);
            if (node == null || !mc.level.dimension().equals(nodeLocation.dimension)) {
                continue;
            }
            if (worldOf(nodeLocation, nodeLocation.getLocation()).distanceTo(camera) > RANGE) {
                continue;
            }
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
            if (connections == null) {
                continue;
            }
            int hash = node.hashCode();
            for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                TrackNode other = entry.getKey();
                TrackEdge edge = entry.getValue();
                if (!edge.node1.getLocation().dimension.equals(edge.node2.getLocation().dimension)) {
                    continue;
                }
                boolean otherFirst = other.hashCode() > hash;
                if (otherFirst && worldOf(other.getLocation(), other.getLocation().getLocation()).distanceTo(camera) <= RANGE) {
                    continue;
                }
                showEdge(graph, node, other, edge, groups, new Vec3(0, (otherFirst ? 6 : 5) / 64f, 0));
            }
        }
    }

    private static void showEdge(TrackGraph graph, TrackNode node, TrackNode other, TrackEdge edge,
                                 Map<UUID, SignalEdgeGroup> groups, Vec3 lift) {
        EdgeData data = edge.getEdgeData();
        Outliner outliner = Outliner.getInstance();
        if (!edge.isTurn()) {
            if (data.hasSignalBoundaries()) {
                showStraightWithSignals(graph, node, other, edge, groups, lift);
                return;
            }
            SignalEdgeGroup single = singleGroup(graph, data, groups);
            if (single != null) {
                outliner.showLine(edge, at(graph, edge, 0, lift), at(graph, edge, 1, lift))
                        .colored(single.color.get()).lineWidth(WIDTH);
            }
            return;
        }
        if (data.hasSignalBoundaries()) {
            showCurveWithSignals(graph, node, other, edge, groups, lift);
        }
        SignalEdgeGroup single = singleGroup(graph, data, groups);
        if (single == null) {
            return;
        }
        BezierConnection turn = edge.getTurn();
        Vec3 previous = null;
        for (int i = 0; i <= turn.getSegmentCount(); i++) {
            Vec3 current = at(graph, edge, i * 1f / turn.getSegmentCount(), lift);
            if (previous != null) {
                outliner.showLine(Pair.of(edge, previous), previous, current)
                        .colored(single.color.get()).lineWidth(WIDTH);
            }
            previous = current;
        }
    }

    private static void showStraightWithSignals(TrackGraph graph, TrackNode node, TrackNode other, TrackEdge edge,
                                                Map<UUID, SignalEdgeGroup> groups, Vec3 lift) {
        Outliner outliner = Outliner.getInstance();
        double length = edge.getLength();
        double prev = 0;
        SignalBoundary prevBoundary = null;
        for (TrackEdgePoint point : edge.getEdgeData().getPoints()) {
            if (!(point instanceof SignalBoundary boundary)) {
                continue;
            }
            prevBoundary = boundary;
            SignalEdgeGroup group = groups.get(boundary.getGroup(node));
            double end = boundary.getLocationOn(edge) / length;
            if (group != null) {
                outliner.showLine(Pair.of(boundary, edge),
                                at(graph, edge, prev + (prev == 0 ? 0 : 1 / 16f / length), lift),
                                at(graph, edge, end - 1 / 16f / length, lift))
                        .colored(group.color.get()).lineWidth(WIDTH);
            }
            prev = end;
        }
        if (prevBoundary == null) {
            return;
        }
        SignalEdgeGroup group = groups.get(prevBoundary.getGroup(other));
        if (group != null) {
            outliner.showLine(edge, at(graph, edge, prev + 1 / 16f / length, lift), at(graph, edge, 1, lift))
                    .colored(group.color.get()).lineWidth(WIDTH);
        }
    }

    private static void showCurveWithSignals(TrackGraph graph, TrackNode node, TrackNode other, TrackEdge edge,
                                             Map<UUID, SignalEdgeGroup> groups, Vec3 lift) {
        Outliner outliner = Outliner.getInstance();
        Iterator<TrackEdgePoint> points = edge.getEdgeData().getPoints().iterator();
        SignalBoundary boundary = nextBoundary(points);
        if (boundary == null) {
            return;
        }
        SignalEdgeGroup initial = groups.get(boundary.getGroup(node));
        if (initial == null) {
            return;
        }
        double boundaryAt = boundary.getLocationOn(edge);
        Color colour = initial.color.get();
        Vec3 previous = null;
        BezierConnection turn = edge.getTurn();
        for (int i = 0; i <= turn.getSegmentCount(); i++) {
            double f = i * 1f / turn.getSegmentCount();
            Vec3 current = at(graph, edge, f, lift);
            if (previous != null) {
                if (boundary != null && f * turn.getLength() > boundaryAt) {
                    current = at(graph, edge, (boundaryAt - WIDTH) / turn.getLength(), lift);
                    outliner.showLine(Pair.of(edge, previous), previous, current)
                            .colored(colour).lineWidth(WIDTH);
                    current = at(graph, edge, (boundaryAt + WIDTH) / turn.getLength(), lift);
                    previous = current;
                    UUID next = boundary.getGroup(other);
                    if (next != null && groups.containsKey(next)) {
                        colour = groups.get(next).color.get();
                    }
                    boundary = nextBoundary(points);
                    boundaryAt = boundary == null ? 0 : boundary.getLocationOn(edge);
                }
                outliner.showLine(Pair.of(edge, previous), previous, current).colored(colour).lineWidth(WIDTH);
            }
            previous = current;
        }
    }

    private static SignalBoundary nextBoundary(Iterator<TrackEdgePoint> points) {
        while (points.hasNext()) {
            if (points.next() instanceof SignalBoundary boundary) {
                return boundary;
            }
        }
        return null;
    }

    private static SignalEdgeGroup singleGroup(TrackGraph graph, EdgeData data, Map<UUID, SignalEdgeGroup> groups) {
        UUID id = data.getEffectiveEdgeGroupId(graph);
        return id == null ? null : groups.get(id);
    }

    private static Vec3 at(TrackGraph graph, TrackEdge edge, double t, Vec3 lift) {
        return worldOf(edge.node1.getLocation(), edge.getPosition(graph, t).add(lift));
    }

    private static Vec3 worldOf(TrackNodeLocation frame, Vec3 local) {
        SubLevel host = RailSeams.clientHostOf(frame);
        if (host == null) {
            return local;
        }
        Vector3d world = host.logicalPose().transformPosition(new Vector3d(local.x, local.y, local.z), new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }
}
