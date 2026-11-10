package com.lycoris.loconautics.content.schedule;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SchedulePlanner {

    public static double aheadOnCurrentEdge(TravellingPoint point, TrackNode a, TrackNode b, TrackGraphLocation loc) {
        if (point == null || point.edge == null) {
            return Double.NaN;
        }
        if (point.node1 == a && point.node2 == b) {
            double ahead = loc.position - point.position;
            return ahead > 0.0 ? ahead : Double.NaN;
        }
        if (point.node1 == b && point.node2 == a) {
            double behind = point.position - (point.edge.getLength() - loc.position);
            return behind > 0.0 ? -behind : Double.NaN;
        }
        return Double.NaN;
    }

    public static TrackGraphLocation locationOf(TrackGraph graph, GlobalStation station) {
        TrackGraphLocation loc = new TrackGraphLocation();
        loc.graph = graph;
        loc.edge = station.edgeLocation;
        loc.position = station.position;
        return loc;
    }

    @Nullable
    public static SchedulePath planTo(TrackGraph graph, TravellingPoint from, TrackGraphLocation loc,
                                      @Nullable GlobalStation station, boolean directed) {
        return planTo(graph, from, loc, station, directed, SchedulePathfinder.Routing.NONE);
    }

    @Nullable
    public static SchedulePath planTo(TrackGraph graph, TravellingPoint from, TrackGraphLocation loc,
                                      @Nullable GlobalStation station, boolean directed,
                                      SchedulePathfinder.Routing routing) {
        TrackNode a = graph.locateNode(loc.edge.getFirst());
        TrackNode b = graph.locateNode(loc.edge.getSecond());
        if (a == null || b == null) {
            return null;
        }
        double ahead = aheadOnCurrentEdge(from, a, b, loc);
        if (!Double.isNaN(ahead)) {
            return new SchedulePath(loc, ahead > 0.0 ? 1 : -1, ImmutableList.of(), station);
        }
        SchedulePathfinder.Path found = directed
                ? SchedulePathfinder.find(graph, from, a, b, true, routing)
                : SchedulePathfinder.find(graph, from, a, b, false, routing);
        if (found == null) {
            return null;
        }
        SchedulePath turntable = toTurntable(graph, found, null);
        return turntable != null ? turntable
                : new SchedulePath(loc, found.direction(), found.edges(), station);
    }

    @Nullable
    public static SchedulePath toTurntable(TrackGraph graph, SchedulePathfinder.Path found,
                                           @Nullable Consumer<String> why) {
        TurntableRegistry.Exit hop = found.hop();
        if (hop == null) {
            return null;
        }
        List<Couple<TrackNode>> route = new ArrayList<>(found.edgesToHop());
        if (hop.entry()) {
            return toLegEnd(graph, found, hop, route, why);
        }
        List<Couple<TrackNode>> plateRoute = TurntableRegistry.plateRouteFrom(graph, hop.plate(), hop.entryHead());
        if (plateRoute == null || plateRoute.isEmpty()) {
            reject(why, "no chain of plate edges leads across the turntable");
            return null;
        }
        TrackGraphLocation center = TurntableRegistry.centerOf(graph, plateRoute);
        if (center == null) {
            reject(why, "the turntable has no measurable center");
            return null;
        }
        route.addAll(plateRoute);
        return new SchedulePath(center, found.direction(), route, null, hop);
    }

    @Nullable
    private static SchedulePath toLegEnd(TrackGraph graph, SchedulePathfinder.Path found,
                                         TurntableRegistry.Exit hop, List<Couple<TrackNode>> route,
                                         @Nullable Consumer<String> why) {
        TrackNode from = graph.locateNode(hop.approachFrom());
        TrackNode end = graph.locateNode(hop.worldHead());
        TrackEdge approach = from == null || end == null ? null : graph.getConnection(Couple.create(from, end));
        if (approach == null) {
            reject(why, "the leg before the turntable has no edge into its end");
            return null;
        }
        TrackGraphLocation stop = new TrackGraphLocation();
        stop.graph = graph;
        stop.edge = Couple.create(from.getLocation(), end.getLocation());
        stop.position = approach.getLength();
        return new SchedulePath(stop, found.direction(), route, null, hop);
    }

    private static void reject(@Nullable Consumer<String> why, String reason) {
        if (why != null) {
            why.accept(reason);
        }
    }

    private SchedulePlanner() {
    }
}
