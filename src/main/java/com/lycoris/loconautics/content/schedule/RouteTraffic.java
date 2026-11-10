package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgeData;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RouteTraffic {

    private static final int STATION = 50;
    static final int STATION_WITH_TRAIN = 300;
    private static final int MANUAL_TRAIN = 200;
    private static final int IDLE_TRAIN = 700;
    private static final int ARRIVING_TRAIN = 50;
    private static final int WAITING_TRAIN = 50;
    private static final int ANY_TRAIN = 25;
    private static final int RED_SIGNAL = 25;
    private static final int REDSTONE_RED_SIGNAL = 400;
    private static final double ARRIVING_DISTANCE = 50.0;
    private static final int PLATE_HOP = 80;
    private static final int PLATE_HEAT = 60;
    private static final int PLATE_HEAT_CAP = 300;

    private final SableTrain self;
    private final Set<UUID> consist;
    private final Map<TrackEdge, Integer> trainPenalties = new IdentityHashMap<>();
    private final Set<UUID> parkedStations = new HashSet<>();

    private RouteTraffic(SableTrain self) {
        this.self = self;
        this.consist = TrainSignals.consistOf(self);
    }

    public static RouteTraffic around(SableTrain self, TrackGraph graph) {
        RouteTraffic traffic = new RouteTraffic(self);
        Set<UUID> counted = new HashSet<>(traffic.consist);
        for (SableTrain other : SableTrainRegistry.all()) {
            if (counted.add(other.id())) {
                Set<UUID> cars = TrainSignals.consistOf(other);
                counted.addAll(cars);
                traffic.addSableConsist(cars, graph);
            }
        }
        for (Train other : Create.RAILWAYS.trains.values()) {
            if (other.graph == graph && !CreateTrainShadow.isShadow(other)) {
                Set<TrackEdge> edges = Collections.newSetFromMap(new IdentityHashMap<>());
                other.getEndpointEdges().forEach(nodes -> collectEdges(graph, nodes, edges));
                traffic.penalize(edges, other.getNavigationPenalty());
                GlobalStation station = other.getCurrentStation();
                if (station != null) {
                    traffic.parkedStations.add(station.id);
                }
            }
        }
        return traffic;
    }

    private void addSableConsist(Set<UUID> cars, TrackGraph graph) {
        Set<TrackEdge> edges = Collections.newSetFromMap(new IdentityHashMap<>());
        int penalty = IDLE_TRAIN;
        for (UUID id : cars) {
            SableTrain car = SableTrainRegistry.get(id);
            if (car == null) {
                continue;
            }
            penalty = Math.min(penalty, penaltyOf(car));
            GlobalStation station = car.atStation();
            if (station != null) {
                parkedStations.add(station.id);
            }
            RailCarriage rail = car.car() == null ? null : car.car().carriage();
            if (rail != null && rail.graph() == graph) {
                collectPoint(graph, rail.leading(), edges);
                collectPoint(graph, rail.trailing(), edges);
            }
        }
        penalize(edges, penalty);
    }

    private static int penaltyOf(SableTrain train) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
        boolean scheduled = runtime != null && runtime.schedule != null && !runtime.completed && !runtime.paused;
        if (!scheduled) {
            return Math.abs(train.speed()) > StationLanding.PARK_SPEED ? MANUAL_TRAIN : IDLE_TRAIN;
        }
        if (TrainSignals.isHeld(train.id())) {
            return WAITING_TRAIN;
        }
        double remaining = runtime.remainingToTarget();
        return remaining != RouteDistance.UNKNOWN && remaining < ARRIVING_DISTANCE ? ARRIVING_TRAIN : ANY_TRAIN;
    }

    private static void collectPoint(TrackGraph graph, TravellingPoint point, Set<TrackEdge> edges) {
        if (point != null) {
            collectEdges(graph, Couple.create(point.node1, point.node2), edges);
        }
    }

    private static void collectEdges(TrackGraph graph, Couple<TrackNode> nodes, Set<TrackEdge> edges) {
        if (nodes.getFirst() == null || nodes.getSecond() == null) {
            return;
        }
        TrackEdge forward = graph.getConnection(nodes);
        TrackEdge backward = graph.getConnection(nodes.swap());
        if (forward != null) {
            edges.add(forward);
        }
        if (backward != null) {
            edges.add(backward);
        }
    }

    private void penalize(Set<TrackEdge> edges, int penalty) {
        for (TrackEdge edge : edges) {
            trainPenalties.merge(edge, penalty / 2, Integer::sum);
        }
    }

    int plateCost(UUID plate) {
        if (plate == null || TurntableRegistry.consistOnPlate(plate, consist)) {
            return 0;
        }
        int cost = PLATE_HOP + Math.min(TurntableRegistry.heatOf(plate) * PLATE_HEAT, PLATE_HEAT_CAP);
        return TurntableRegistry.otherTrainOnPlate(plate, consist) ? cost + STATION_WITH_TRAIN : cost;
    }

    int trainsOn(TrackEdge edge) {
        return trainPenalties.getOrDefault(edge, 0);
    }

    int pointsOn(TrackEdge edge, TrackNode towards) {
        EdgeData data = edge.getEdgeData();
        if (!data.hasPoints()) {
            return 0;
        }
        int penalty = 0;
        for (TrackEdgePoint point : data.getPoints()) {
            if (point instanceof SignalBoundary signal) {
                penalty += signalPenalty(signal, towards);
            } else if (point instanceof GlobalStation station) {
                penalty += stationPenalty(station);
            }
        }
        return penalty;
    }

    private int signalPenalty(SignalBoundary signal, TrackNode towards) {
        if (signal.isForcedRed(towards)) {
            return REDSTONE_RED_SIGNAL;
        }
        UUID groupId = signal.getGroup(towards);
        SignalEdgeGroup group = groupId == null ? null : Create.RAILWAYS.signalEdgeGroups.get(groupId);
        return group != null && TrainSignals.blockedFor(group, consist) ? RED_SIGNAL : 0;
    }

    private int stationPenalty(GlobalStation station) {
        if (TrainStations.sameStation(station, self.atStation())) {
            return 0;
        }
        return parkedStations.contains(station.id) ? STATION + STATION_WITH_TRAIN : STATION;
    }
}
