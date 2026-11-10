package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.world.phys.Vec3;

public final class RailFollower {

    private final TrackGraph graph;
    private final TravellingPoint point;
    private final ITrackSelector selector;
    private double speed;
    private Vec3 lastPos;

    private RailFollower(TrackGraph graph, TravellingPoint point, Vec3 upNormal, double speed) {
        this.graph = graph;
        this.point = point;
        this.speed = speed;
        this.selector = point.steer(SteerDirection.NONE, upNormal);
        // seed lastpos so positiontick never return null before the first successful advance
        this.lastPos = point.edge != null ? point.getPosition(graph) : Vec3.ZERO;
    }

    public static RailFollower at(TrackGraphLocation location, Vec3 upNormal, double speed) {
        TravellingPoint point = pointAt(location);
        if (point == null) {
            return null;
        }
        return new RailFollower(location.graph, point, upNormal, speed);
    }

    public static TravellingPoint pointAt(TrackGraphLocation location) {
        TrackGraph graph = location.graph;
        TrackNode node1 = graph.locateNode(location.edge.getFirst());
        TrackNode node2 = graph.locateNode(location.edge.getSecond());
        if (node1 == null || node2 == null) {
            return null;
        }
        TrackEdge edge = graph.getConnectionsFrom(node1).get(node2);
        if (edge == null) {
            return null;
        }
        return new TravellingPoint(node1, node2, edge, location.position, false);
    }

    public Vec3 tick() {
        if (point.edge == null) {
            return lastPos;
        }
        point.travel(graph, speed, selector, point.ignoreEdgePoints(), point.ignoreTurns(), point.ignorePortals());
        if (point.blocked && point.edge != null) {
            point.reverse(graph);
        }
        if (point.edge == null) {
            return lastPos;
        }
        lastPos = point.getPosition(graph);
        return lastPos;
    }

    public Vec3 position() {
        return point.edge == null ? lastPos : point.getPosition(graph);
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
}
