package com.lycoris.loconautics.content.trainmail;

import com.lycoris.loconautics.content.schedule.SchedulePathfinder;
import com.lycoris.loconautics.content.schedule.SchedulePlanner;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PostalRoute {

    public record Order(List<GlobalStation> stops, double cost) {
    }

    private static final int MAX_EXACT_STOPS = 7;

    private final PostalWorld<?> world;
    private final List<GlobalStation> offices;
    private final Map<String, Double> costs = new HashMap<>();

    public PostalRoute(PostalWorld<?> world, List<GlobalStation> offices) {
        this.world = world;
        this.offices = offices;
    }

    public double leg(@Nullable GlobalStation from, GlobalStation to) {
        String key = (from == null ? "train" : from.id.toString()) + ">" + to.id;
        return costs.computeIfAbsent(key, k -> world.legCost(from, to));
    }

    public boolean reachable(GlobalStation station) {
        return Double.isFinite(leg(null, station));
    }

    public Order cheapest(List<GlobalStation> stops) {
        if (stops.size() > MAX_EXACT_STOPS) {
            return nearestFirst(stops);
        }
        Search search = new Search();
        search.extend(null, new ArrayList<>(), new ArrayList<>(stops), 0.0);
        return search.best.isEmpty() ? nearestFirst(stops) : new Order(search.best, search.bestCost);
    }

    private final class Search {
        List<GlobalStation> best = List.of();
        double bestCost = Double.POSITIVE_INFINITY;

        void extend(@Nullable GlobalStation from, List<GlobalStation> done, List<GlobalStation> left, double cost) {
            if (cost >= bestCost) {
                return;
            }
            if (left.isEmpty()) {
                double total = cost + home(from);
                if (total < bestCost) {
                    bestCost = total;
                    best = new ArrayList<>(done);
                }
                return;
            }
            for (int i = 0; i < left.size(); i++) {
                GlobalStation next = left.remove(i);
                done.add(next);
                extend(next, done, left, cost + leg(from, next));
                done.remove(done.size() - 1);
                left.add(i, next);
            }
        }
    }

    private Order nearestFirst(List<GlobalStation> stops) {
        List<GlobalStation> left = new ArrayList<>(stops);
        List<GlobalStation> done = new ArrayList<>();
        GlobalStation from = null;
        double total = 0.0;
        while (!left.isEmpty()) {
            GlobalStation nearest = left.get(0);
            for (GlobalStation candidate : left) {
                if (leg(from, candidate) < leg(from, nearest)) {
                    nearest = candidate;
                }
            }
            total += leg(from, nearest);
            left.remove(nearest);
            done.add(nearest);
            from = nearest;
        }
        return new Order(done, total + home(from));
    }

    private double home(@Nullable GlobalStation last) {
        if (last == null) {
            return 0.0;
        }
        double best = Double.POSITIVE_INFINITY;
        for (GlobalStation office : offices) {
            best = Math.min(best, leg(last, office));
        }
        return Double.isFinite(best) ? best : 0.0;
    }

    public static double between(TrackGraph graph, GlobalStation from, GlobalStation to,
                                 SchedulePathfinder.Routing routing) {
        TrackNode fromA = graph.locateNode(from.edgeLocation.getFirst());
        TrackNode fromB = graph.locateNode(from.edgeLocation.getSecond());
        TrackNode toA = graph.locateNode(to.edgeLocation.getFirst());
        TrackNode toB = graph.locateNode(to.edgeLocation.getSecond());
        if (fromA == null || fromB == null || toA == null || toB == null) {
            return Double.POSITIVE_INFINITY;
        }
        TrackEdge edge = graph.getConnection(Couple.create(fromA, fromB));
        if (edge == null) {
            return Double.POSITIVE_INFINITY;
        }
        TravellingPoint point = new TravellingPoint(fromA, fromB, edge, from.position, false);
        TrackGraphLocation target = SchedulePlanner.locationOf(graph, to);
        double ahead = SchedulePlanner.aheadOnCurrentEdge(point, toA, toB, target);
        if (!Double.isNaN(ahead)) {
            return Math.abs(ahead);
        }
        SchedulePathfinder.Path found = SchedulePathfinder.find(graph, point, toA, toB, true, routing);
        return found == null ? Double.POSITIVE_INFINITY : found.cost();
    }
}
