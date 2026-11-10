package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.createmod.catnip.data.Couple;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class StationBerth {

    record Berth(SableTrain parked, TrackGraphLocation stop, double free, double need, boolean local) {

        boolean fits() {
            return free + 1.0e-6 >= need;
        }
    }

    private StationBerth() {
    }

    @Nullable
    static Berth measure(TrackGraph graph, @Nullable TravellingPoint lead, List<Couple<TrackNode>> route,
                         SableTrain parked, double ownLength) {
        if (graph == null || lead == null || lead.edge == null || lead.node1 == null || lead.node2 == null) {
            return null;
        }
        TrackGraphLocation near = null;
        TravellingPoint nearPoint = null;
        double toNear = Double.MAX_VALUE;
        for (ConsistNose.Nose end : List.of(ConsistNose.of(parked), ConsistNose.tail(parked))) {
            TrackGraphLocation loc = locationOf(end.point());
            if (loc == null) {
                continue;
            }
            RouteDistance.Measure measured = RouteDistance.measure(lead, graph, route, loc);
            if (measured.known() && measured.distance() < toNear) {
                toNear = measured.distance();
                near = loc;
                nearPoint = end.point();
            }
        }
        if (near == null) {
            return null;
        }
        double gap = LoconauticsConstants.MANEUVER_BERTH_GAP;
        double need = gap + ownLength + LoconauticsConstants.MANEUVER_REVERSAL_MARGIN;
        double back = Math.min(gap, toNear);
        // the loco itself already stands on the clear track behind its nose
        double reach = toNear + ownLength;
        if (RouteDistance.onTargetEdge(lead, near)) {
            trace(graph, parked, nearPoint, toNear, back, reach, "none on the lead edge");
            return new Berth(parked, onLeadEdge(graph, lead, near, back), reach, need, true);
        }
        int index = indexOf(route, near);
        if (index < 0) {
            return null;
        }
        double behind = clearBehind(graph, route, near, index);
        double free = Math.min(reach, behind);
        trace(graph, parked, nearPoint, toNear, back, reach, junctionText(graph, route, index) + " " + behind + " behind");
        TrackGraphLocation stop = retreat(graph, route, near, index, back);
        return new Berth(parked, stop, free, need, RouteDistance.onTargetEdge(lead, stop));
    }

    // temporary, where the parked end sits and which switch limited the free length
    private static void trace(TrackGraph graph, SableTrain parked, TravellingPoint end, double toNear, double back,
                              double reach, String junction) {
        Vec3 world = end == null ? null : end.getPosition(graph);
        LoconauticsConstants.LOGGER.info("[maneuver] berth measure: train {} near wheel {} is {} from the lead end, "
                        + "stop is {} short of it, reach {}, last switch {}", ConsistNose.shortId(parked.id()),
                world == null ? "unknown" : String.format("(%.1f,%.1f,%.1f)", world.x, world.y, world.z),
                String.format("%.2f", toNear), String.format("%.2f", back), String.format("%.2f", reach), junction);
    }

    private static String junctionText(TrackGraph graph, List<Couple<TrackNode>> route, int index) {
        for (int i = index; i >= 0; i--) {
            TrackNode node = route.get(i).getFirst();
            if (graph.getConnectionsFrom(node).size() > 2) {
                return node.getLocation().getLocation().toString();
            }
        }
        return "none on the route";
    }

    @Nullable
    private static TrackGraphLocation locationOf(@Nullable TravellingPoint point) {
        if (point == null || point.edge == null || point.node1 == null || point.node2 == null) {
            return null;
        }
        TrackGraphLocation loc = new TrackGraphLocation();
        loc.edge = Couple.create(point.node1.getLocation(), point.node2.getLocation());
        loc.position = point.position;
        return loc;
    }

    private static TrackGraphLocation onLeadEdge(TrackGraph graph, TravellingPoint lead, TrackGraphLocation near,
                                                 double back) {
        boolean sameSense = lead.node1.getLocation().equals(near.edge.getFirst());
        double nearAt = sameSense ? near.position : lead.edge.getLength() - near.position;
        double side = nearAt >= lead.position ? 1.0 : -1.0;
        TrackGraphLocation stop = new TrackGraphLocation();
        stop.graph = graph;
        stop.edge = Couple.create(lead.node1.getLocation(), lead.node2.getLocation());
        stop.position = Math.max(0.0, Math.min(lead.edge.getLength(), nearAt - side * back));
        return stop;
    }

    private static int indexOf(List<Couple<TrackNode>> route, TrackGraphLocation loc) {
        for (int i = 0; i < route.size(); i++) {
            Couple<TrackNode> hop = route.get(i);
            boolean forward = hop.getFirst().getLocation().equals(loc.edge.getFirst())
                    && hop.getSecond().getLocation().equals(loc.edge.getSecond());
            boolean backward = hop.getFirst().getLocation().equals(loc.edge.getSecond())
                    && hop.getSecond().getLocation().equals(loc.edge.getFirst());
            if (forward || backward) {
                return i;
            }
        }
        return -1;
    }

    private static double lengthOf(TrackGraph graph, Couple<TrackNode> hop) {
        TrackEdge edge = graph.getConnection(hop);
        return edge == null ? 0.0 : edge.getLength();
    }

    // along the hop in route order, from the hop start
    private static double positionIn(TrackGraph graph, Couple<TrackNode> hop, TrackGraphLocation loc) {
        boolean sameSense = hop.getFirst().getLocation().equals(loc.edge.getFirst());
        return sameSense ? loc.position : lengthOf(graph, hop) - loc.position;
    }

    // distance from the parked end back to the last switch on the route
    private static double clearBehind(TrackGraph graph, List<Couple<TrackNode>> route, TrackGraphLocation near,
                                      int index) {
        double walked = positionIn(graph, route.get(index), near);
        for (int i = index; i >= 0; i--) {
            if (graph.getConnectionsFrom(route.get(i).getFirst()).size() > 2) {
                return walked;
            }
            if (i > 0) {
                walked += lengthOf(graph, route.get(i - 1));
            }
        }
        return Double.MAX_VALUE;
    }

    private static TrackGraphLocation retreat(TrackGraph graph, List<Couple<TrackNode>> route,
                                              TrackGraphLocation near, int index, double back) {
        int i = index;
        double at = positionIn(graph, route.get(i), near);
        double left = back;
        while (left > at && i > 0) {
            left -= at;
            i--;
            at = lengthOf(graph, route.get(i));
        }
        TrackGraphLocation stop = new TrackGraphLocation();
        stop.graph = graph;
        stop.edge = Couple.create(route.get(i).getFirst().getLocation(), route.get(i).getSecond().getLocation());
        stop.position = Math.max(0.0, at - left);
        return stop;
    }
}
