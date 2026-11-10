package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgeData;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import net.createmod.catnip.data.Couple;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class SchedulePathfinder {

    private static final double DEGREE_COST = 1.0;
    private static final double AXLE_TRAILING_COST = 20.0;
    // a stop at the very end of a track would derail the nose wheel
    private static final double DEAD_END_STOP_GAP = 2.0;

    public record Routing(boolean turntables, double trainLength, boolean reversible,
                          @Nullable RouteTraffic traffic, @Nullable Boolean axleLeads, boolean noseAlong, int rel,
                          @Nullable PlateOffers offers) {

        public static final Routing NONE = new Routing(false, 0.0, false, null, null, false, 0, null);

        public static Routing through(double trainLength) {
            return new Routing(true, trainLength, false, null, null, false, 0, null);
        }

        public Routing around(RouteTraffic traffic) {
            return new Routing(turntables, trainLength, reversible, traffic, axleLeads, noseAlong, rel, offers);
        }

        public Routing reversing(boolean reversible) {
            return new Routing(turntables, trainLength, reversible, traffic, axleLeads, noseAlong, rel, offers);
        }

        public Routing axleLeading(@Nullable Boolean axleLeads) {
            return new Routing(turntables, trainLength, reversible, traffic, axleLeads, noseAlong, rel, offers);
        }

        public Routing arriving(boolean noseAlong, int rel) {
            return new Routing(turntables, trainLength, reversible, traffic, axleLeads, noseAlong, rel, offers);
        }

        public Routing counting(@Nullable PlateOffers offers) {
            return new Routing(turntables, trainLength, reversible, traffic, axleLeads, noseAlong, rel, offers);
        }

        static int relAfter(TurntableRegistry.Exit exit, int rel) {
            return exit.entry() || !ManeuverPlan.backsOut(exit) ? rel : -rel;
        }

        boolean lands(boolean travelAlongFacing, int legRel) {
            return !noseAlong || travelAlongFacing == legRel > 0;
        }

        double axleCost(boolean departsAgainstSense) {
            return axleLeads != null && axleLeads == departsAgainstSense ? AXLE_TRAILING_COST : 0.0;
        }
    }

    public static final class PlateOffers {
        private int offered;

        void count() {
            offered++;
        }

        public int offered() {
            return offered;
        }
    }

    public record Path(int direction, double distance, double cost, List<Couple<TrackNode>> edges,
                       @Nullable TurntableRegistry.Exit hop, List<Couple<TrackNode>> edgesToHop, int landRel) {
    }

    private SchedulePathfinder() {
    }

    public static Path find(TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB) {
        return find(graph, leading, targetA, targetB, false, Routing.NONE);
    }

    public static Path find(TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB,
                            boolean directed) {
        return find(graph, leading, targetA, targetB, directed, Routing.NONE);
    }

    public static Path find(TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB,
                            boolean directed, Routing routing) {
        if (leading.node1 == null || leading.node2 == null || targetA == null || targetB == null) {
            return null;
        }
        Result forward = search(graph, leading.node1, leading.node2, targetA, targetB, directed, routing);
        Result reverse = search(graph, leading.node2, leading.node1, targetA, targetB, directed, routing);

        if (forward == null && reverse == null) {
            return null;
        }
        boolean useForward = forward != null && (reverse == null || forward.cost <= reverse.cost);
        Result chosen = useForward ? forward : reverse;
        return pathOf(useForward ? 1 : -1, chosen);
    }

    public static Path findAhead(TrackGraph graph, TravellingPoint leading,
                                 TrackNode targetA, TrackNode targetB, int sense) {
        return findAhead(graph, leading, targetA, targetB, sense, false, Routing.NONE);
    }

    public static Path findAhead(TrackGraph graph, TravellingPoint leading,
                                 TrackNode targetA, TrackNode targetB, int sense, boolean directed) {
        return findAhead(graph, leading, targetA, targetB, sense, directed, Routing.NONE);
    }

    public static Path findAhead(TrackGraph graph, TravellingPoint leading,
                                 TrackNode targetA, TrackNode targetB, int sense, boolean directed,
                                 Routing routing) {
        if (leading.node1 == null || leading.node2 == null || targetA == null || targetB == null) {
            return null;
        }
        Result found = sense >= 0
                ? search(graph, leading.node1, leading.node2, targetA, targetB, directed, routing)
                : search(graph, leading.node2, leading.node1, targetA, targetB, directed, routing);
        return found == null ? null : pathOf(sense >= 0 ? 1 : -1, found);
    }

    private static Path pathOf(int direction, Result found) {
        return new Path(direction, found.distance(), found.cost(), found.edges(), found.hop(),
                found.hop() == null ? found.edges() : found.edgesToHop(), found.landRel());
    }

    private record Result(double distance, double cost, List<Couple<TrackNode>> edges,
                          @Nullable TurntableRegistry.Exit hop, List<Couple<TrackNode>> edgesToHop, int landRel) {
    }

    private record Frontier(TrackNode node, TrackNode cameFrom, double distance, double cost,
                            List<Couple<TrackNode>> edges, @Nullable TurntableRegistry.Exit hop,
                            List<Couple<TrackNode>> edgesToHop, int rel, boolean arrived)
            implements Comparable<Frontier> {

        Frontier(TrackNode node, TrackNode cameFrom, double distance, double cost,
                 List<Couple<TrackNode>> edges, @Nullable TurntableRegistry.Exit hop,
                 List<Couple<TrackNode>> edgesToHop, int rel) {
            this(node, cameFrom, distance, cost, edges, hop, edgesToHop, rel, false);
        }

        @Override
        public int compareTo(Frontier o) {
            return Double.compare(cost, o.cost);
        }
    }

    private record Visit(Couple<TrackNode> edge, int rel) {
    }

    private static Result search(TrackGraph graph, TrackNode cameFrom, TrackNode start,
                                 TrackNode targetA, TrackNode targetB, boolean directed, Routing routing) {
        PriorityQueue<Frontier> queue = new PriorityQueue<>();
        queue.add(new Frontier(start, cameFrom, 0.0, 0.0, new ArrayList<>(), null, List.of(), routing.rel()));
        Set<Visit> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Frontier f = queue.poll();
            if (f.arrived) {
                return new Result(f.distance, f.cost, f.edges, f.hop, f.edgesToHop, f.rel);
            }
            if (!visited.add(new Visit(Couple.create(f.cameFrom, f.node), f.rel))) {
                continue;
            }
            TrackEdge arrivedOn = graph.getConnection(Couple.create(f.cameFrom, f.node));
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(f.node);
            for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                TrackNode next = entry.getKey();
                if (next == f.cameFrom) {
                    continue;
                }
                if (!stepAllowed(arrivedOn, f.node, next, entry.getValue())) {
                    continue;
                }
                List<Couple<TrackNode>> edges = new ArrayList<>(f.edges);
                edges.add(Couple.create(f.node, next));
                TrackEdge edge = entry.getValue();
                double distance = f.distance + edge.getLength();
                RouteTraffic traffic = routing.traffic();
                double cost = f.cost + edge.getLength() + (traffic == null ? 0 : traffic.trainsOn(edge));
                if (isTarget(f.node, next, targetA, targetB, directed)) {
                    boolean along = f.node == targetA && next == targetB;
                    boolean lands = routing.lands(along, f.rel);
                    if (lands) {
                        queue.add(new Frontier(next, f.node, distance, cost, edges, f.hop, f.edgesToHop, f.rel, true));
                    }
                    continue;
                }
                if (!canNavigateVia(edge, next)) {
                    continue;
                }
                if (traffic != null) {
                    cost += traffic.pointsOn(edge, next);
                }
                queue.add(new Frontier(next, f.node, distance, cost, edges, f.hop, f.edgesToHop, f.rel));
            }
            queueTurntableExits(graph, f, queue, routing);
        }
        return null;
    }

    private static boolean stepAllowed(@Nullable TrackEdge arrivedOn, TrackNode node, TrackNode next, TrackEdge edge) {
        if (arrivedOn != null && !arrivedOn.canTravelTo(edge)) {
            return false;
        }
        return !(edge.isInterDimensional() && RailSeams.isSeam(node.getLocation(), next.getLocation())
                && !RailSeams.aligned(node.getLocation(), next.getLocation()));
    }

    public record Reversal(TrackNode from, TrackNode to, double position, double cost, double past,
                           List<Couple<TrackNode>> edges) {
    }

    private record Walk(TrackNode node, TrackNode cameFrom, double cost, double past,
                        List<Couple<TrackNode>> edges) implements Comparable<Walk> {

        @Override
        public int compareTo(Walk o) {
            return Double.compare(cost, o.cost);
        }
    }

    public static Path findFrom(TrackGraph graph, TrackNode cameFrom, TrackNode start,
                                TrackNode targetA, TrackNode targetB, boolean directed, Routing routing) {
        if (cameFrom == null || start == null || targetA == null || targetB == null) {
            return null;
        }
        Result found = search(graph, cameFrom, start, targetA, targetB, directed, routing);
        return found == null ? null : pathOf(1, found);
    }

    public static List<Reversal> reversalPoints(TrackGraph graph, TravellingPoint leading, int sense,
                                                double clearance) {
        List<Reversal> points = new ArrayList<>();
        if (leading.node1 == null || leading.node2 == null) {
            return points;
        }
        TrackNode cameFrom = sense >= 0 ? leading.node1 : leading.node2;
        TrackNode start = sense >= 0 ? leading.node2 : leading.node1;
        PriorityQueue<Walk> queue = new PriorityQueue<>();
        queue.add(new Walk(start, cameFrom, 0.0, 0.0, new ArrayList<>()));
        Set<Couple<TrackNode>> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Walk w = queue.poll();
            if (!visited.add(Couple.create(w.cameFrom, w.node))) {
                continue;
            }
            TrackEdge arrivedOn = graph.getConnection(Couple.create(w.cameFrom, w.node));
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(w.node);
            double past = connections.size() > 2 ? 0.0 : w.past;
            boolean onward = false;
            for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                TrackNode next = entry.getKey();
                TrackEdge edge = entry.getValue();
                if (next == w.cameFrom || !stepAllowed(arrivedOn, w.node, next, edge)
                        || !canNavigateVia(edge, next)) {
                    continue;
                }
                onward = true;
                List<Couple<TrackNode>> edges = new ArrayList<>(w.edges);
                edges.add(Couple.create(w.node, next));
                double need = clearance - past;
                if (need <= edge.getLength()) {
                    double stopAt = Math.max(0.0, need);
                    points.add(new Reversal(w.node, next, stopAt, w.cost, past + stopAt, edges));
                }
                queue.add(new Walk(next, w.node, w.cost + edge.getLength(), past + edge.getLength(), edges));
            }
            if (!onward && arrivedOn != null) {
                double cost = w.edges.isEmpty() ? 0.0 : w.cost - arrivedOn.getLength();
                double stop = Math.max(0.0, arrivedOn.getLength() - DEAD_END_STOP_GAP);
                double pastStop = Math.max(0.0, w.past - (arrivedOn.getLength() - stop));
                points.add(new Reversal(w.cameFrom, w.node, stop, cost, pastStop, w.edges));
            }
        }
        points.sort(Comparator.comparingDouble(p -> p.cost() + 2.0 * p.position()));
        return points;
    }

    private static boolean canNavigateVia(TrackEdge edge, TrackNode towards) {
        EdgeData data = edge.getEdgeData();
        if (!data.hasPoints()) {
            return true;
        }
        for (TrackEdgePoint point : data.getPoints()) {
            if (!point.canNavigateVia(towards)) {
                return false;
            }
        }
        return true;
    }

    private static void queueTurntableExits(TrackGraph graph, Frontier f, PriorityQueue<Frontier> queue,
                                            Routing routing) {
        if (!routing.turntables() || !TurntableRegistry.anyRegistered()) {
            return;
        }
        PlateOffers offers = routing.offers();
        List<TurntableRegistry.Exit> exits = TurntableRegistry.exits(graph, f.node, f.cameFrom,
                routing.trainLength(), routing.reversible());
        for (TurntableRegistry.Exit exit : exits) {
            int after = Routing.relAfter(exit, f.rel);
            if (offers != null) {
                offers.count();
            }
            boolean backsOut = ManeuverPlan.backsOut(exit);
            queueHop(f, queue, exit, graph.locateNode(exit.worldHead()), routing.axleCost(backsOut),
                    routing.noseAlong() ? after : f.rel, routing.traffic());
        }
        for (TurntableRegistry.Exit entry : TurntableRegistry.entries(graph, f.node, f.cameFrom,
                routing.trainLength())) {
            if (offers != null) {
                offers.count();
            }
            queueHop(f, queue, entry, graph.locateNode(entry.movingHead()), 0.0, f.rel, routing.traffic());
        }
    }


    private static void queueHop(Frontier f, PriorityQueue<Frontier> queue, TurntableRegistry.Exit exit,
                                 @Nullable TrackNode next, double extraCost, int rel, @Nullable RouteTraffic traffic) {
        if (next == null || next == f.node) {
            return;
        }
        List<Couple<TrackNode>> edges = new ArrayList<>(f.edges);
        edges.add(Couple.create(f.node, next));
        TurntableRegistry.Exit hop = f.hop == null ? exit : f.hop;
        List<Couple<TrackNode>> toHop = f.hop == null ? List.copyOf(f.edges) : f.edgesToHop;
        double turn = exit.degrees() * DEGREE_COST;
        double plate = f.hop == null && traffic != null ? traffic.plateCost(exit.plate()) : 0.0;
        queue.add(new Frontier(next, f.node, f.distance + turn, f.cost + turn + extraCost + plate, edges, hop, toHop,
                rel));
    }

    private static boolean isTarget(TrackNode a, TrackNode b, TrackNode targetA, TrackNode targetB,
                                    boolean directed) {
        if (a == targetA && b == targetB) {
            return true;
        }
        return !directed && a == targetB && b == targetA;
    }
}
