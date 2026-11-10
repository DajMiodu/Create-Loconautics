package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.compat.computercraft.CcEvents;
import com.lycoris.loconautics.compat.computercraft.CcNetworkEvents;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.common.turntable.TurntableSignals;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrainSignals {

    static final double SIGNAL_DECELERATION = StationLanding.DECELERATION;
    static final double SIGNAL_STOP_MARGIN = 1.5;
    static final double SIGNAL_SCAN_MIN = 12.0;
    static final double SIGNAL_STOP_EPSILON = 0.05;
    static final double SPEED_EPSILON = 1.0e-4;
    static final double TARGET_EPSILON = 1.0e-6;

    private static final Map<UUID, Set<UUID>> OCCUPANTS = new HashMap<>();

    private static final Map<UUID, Double> HELD = new HashMap<>();


    private record Reservation(UUID train, UUID boundary) {}

    // rebuilt every tick like creates signaledgegroup.reserved, but kept apart since create clears its own field in the level tick before ours runs
    private static final Map<UUID, Reservation> RESERVED = new HashMap<>();

    private static final Map<UUID, Reservation> PREVIOUS = new HashMap<>();

    private record ChainClaim(UUID boundary, int madeAt) {}

    private static final Map<UUID, Map<UUID, ChainClaim>> CHAINS = new HashMap<>();

    // a claim the train never enters, after a reroute for one, would hold the block forever
    private static final int CHAIN_CLAIM_TTL = 1200;

    static final double CHAIN_REACH = 256.0;

    private static Reservation reservationOf(UUID groupId) {
        Reservation now = RESERVED.get(groupId);
        return now != null ? now : PREVIOUS.get(groupId);
    }

    public static boolean reserved(UUID groupId) {
        return reservationOf(groupId) != null;
    }

    public static boolean reservedOtherThan(UUID groupId, UUID boundaryId) {
        Reservation reservation = reservationOf(groupId);
        return reservation != null && !reservation.boundary().equals(boundaryId);
    }

    public static boolean occupied(UUID groupId) {
        Set<UUID> trains = OCCUPANTS.get(groupId);
        return trains != null && !trains.isEmpty();
    }

    public static Set<UUID> occupantsOf(UUID groupId) {
        Set<UUID> trains = OCCUPANTS.get(groupId);
        return trains == null ? Set.of() : Set.copyOf(trains);
    }

    public static UUID reservedBy(UUID groupId) {
        Reservation reservation = reservationOf(groupId);
        return reservation == null ? null : reservation.train();
    }

    public static Set<UUID> groupsOf(UUID trainId) {
        Set<UUID> out = new HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> entry : OCCUPANTS.entrySet()) {
            if (entry.getValue().contains(trainId)) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    public static boolean occupiedByOther(UUID groupId, UUID trainId) {
        Set<UUID> trains = OCCUPANTS.get(groupId);
        if (trains == null) {
            return false;
        }
        return trains.size() > 1 || (!trains.isEmpty() && !trains.contains(trainId));
    }

    public static void updateOccupancy() {
        OCCUPANTS.clear();
        PREVIOUS.clear();
        PREVIOUS.putAll(RESERVED);
        RESERVED.clear();
        Set<UUID> live = new HashSet<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            live.add(train.id());
            occupy(train);
        }
        seedChainClaims(live);
        HELD.keySet().retainAll(live);
        RAN_RED.retainAll(live);
        com.lycoris.loconautics.compat.computercraft.CcControl.retain(live);
        CcNetworkEvents.tick();
    }

    private static void seedChainClaims(Set<UUID> live) {
        CHAINS.keySet().retainAll(live);
        CHAINS.forEach((trainId, claims) -> {
            claims.entrySet().removeIf(entry -> {
                Set<UUID> inside = OCCUPANTS.get(entry.getKey());
                return (inside != null && inside.contains(trainId))
                        || TrainStations.stationTickCounter - entry.getValue().madeAt() > CHAIN_CLAIM_TTL;
            });
            claims.forEach((groupId, claim) -> RESERVED.put(groupId, new Reservation(trainId, claim.boundary())));
        });
        CHAINS.values().removeIf(Map::isEmpty);
    }

    private static void occupy(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car == null || train.isDerailed()) {
            return;
        }
        if (car.bogeys().isEmpty()) {
            occupyFrom(car.carriage(), train.id());
            return;
        }
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (!train.isBogeyOffTrack(bogey.localPos())) {
                occupyFrom(bogey.rail(), train.id());
            }
        }
    }

    private static void occupyFrom(RailCarriage rail, UUID trainId) {
        if (rail == null) {
            return;
        }
        TrackGraph graph = rail.graph();
        if (graph == null) {
            return;
        }
        occupyAt(graph, rail.leading(), trainId);
        occupyAt(graph, rail.trailing(), trainId);
    }

    private static void occupyAt(TrackGraph graph, TravellingPoint point, UUID trainId) {
        if (point == null || point.edge == null) {
            return;
        }
        UUID group = point.edge.getEdgeData().getGroupAtPosition(graph, point.position);
        if (group == null) {
            return;
        }
        OCCUPANTS.computeIfAbsent(group, key -> new HashSet<>()).add(trainId);
    }

    static double scanRange(double speed) {
        double v = Math.abs(speed);
        return Math.max(SIGNAL_SCAN_MIN, SIGNAL_STOP_MARGIN * (v * v) / (2.0 * SIGNAL_DECELERATION) + SIGNAL_STOP_MARGIN);
    }

    public static double gapToRed(SableTrain train, double direction) {
        return gapToRed(train, direction, scanRange(Math.abs(train.speed())));
    }

    public static double gapToRed(SableTrain train, double direction, double range) {
        if (train.isDerailed() || train.isPhysics() || train.car() == null || direction == 0.0) {
            return Double.NaN;
        }
        double v = Math.abs(train.speed());
        double distance = distanceToRed(train, Math.signum(direction), range, false);
        double gap = Double.isNaN(distance) ? Double.NaN : distance - SignalSnap.stopOffset;
        return gap;
    }

    public static void tickSignals(SableTrain train) {
        if (train.isDerailed() || train.isPhysics() || train.car() == null) {
            release(train);
            CHAINS.remove(train.id());
            return;
        }
        AnalogControllerBlockEntity controller = SableTrainDriver.controllerIn(train);
        boolean scheduled = controller != null ? controller.isScheduleControlled()
                : train.isGhost() && train.isScheduleDriven();
        if (!scheduled) {
            release(train);
            CHAINS.remove(train.id());
            awardRedLight(train);
            return;
        }
        RAN_RED.remove(train.id());
        double speed = train.speed();
        double target = train.targetSpeed();
        Double heldDirection = HELD.get(train.id());

        double direction;
        if (Math.abs(speed) > SPEED_EPSILON) {
            direction = Math.signum(speed);
        } else if (Math.abs(target) > TARGET_EPSILON) {
            direction = Math.signum(target);
        } else if (heldDirection != null) {
            direction = heldDirection;
        } else {
            return;
        }
        if (Math.abs(target) > TARGET_EPSILON && Math.signum(target) != direction) {
            release(train);
            CHAINS.remove(train.id());
            return;
        }
        if (heldDirection == null && Math.abs(target) <= TARGET_EPSILON) {
            return;
        }

        double v = Math.abs(speed);
        double range = scanRange(v);
        double distance = distanceToRed(train, direction, range, true);
        if (Double.isNaN(distance)) {
            release(train);
            return;
        }

        double gap = distance - SIGNAL_STOP_MARGIN;
        double cap = gap <= SIGNAL_STOP_EPSILON ? 0.0 : Math.sqrt(2.0 * SIGNAL_DECELERATION * gap);
        if (cap < Math.abs(target)) {
            train.setTargetSpeed(direction * cap);
            train.setServoBrake(true);
        }
        if (cap > 0.0) {
            return;
        }
        if (Math.abs(speed) <= StationLanding.PARK_SPEED) {
            train.setSpeed(0.0);
        }
        if (HELD.put(train.id(), direction) == null) {
            announce(train, "holding at a red signal");
            CcEvents.queue(train, "train_signal_red", gap);
        }
    }

    public static boolean isHeld(UUID trainId) {
        return HELD.containsKey(trainId);
    }

    public static boolean consistHeldAtRed(SableTrain train) {
        for (UUID id : consistOf(train)) {
            SableScheduleRuntime runtime = SableScheduleRuntime.get(id);
            if (HELD.containsKey(id) || runtime != null && runtime.heldAtSignal()) {
                return true;
            }
        }
        return false;
    }

    static final double RED_LIGHT_RANGE = 2.0;

    private static final Set<UUID> RAN_RED = new HashSet<>();

    private static void awardRedLight(SableTrain train) {
        double speed = train.speed();
        if (Math.abs(speed) <= SPEED_EPSILON) {
            return;
        }
        double distance = distanceToRed(train, Math.signum(speed), RED_LIGHT_RANGE, false);
        if (Double.isNaN(distance)) {
            RAN_RED.remove(train.id());
            return;
        }
        if (!RAN_RED.add(train.id())) {
            return;
        }
        CcEvents.queue(train, "train_signal_passed_at_danger", distance);
        AnalogControllerBlockEntity driven = SableTrainDriver.operatedControllerIn(train);
        ServerPlayer player = driven == null ? null : SableTrainDriver.operatorOf(train, driven);
        if (player != null) {
            AllAdvancements.RED_SIGNAL.awardTo(player);
        }
    }

    private static void release(SableTrain train) {
        if (HELD.remove(train.id()) != null) {
            announce(train, "signal cleared, proceeding");
            CcEvents.queue(train, "train_signal_clear");
        }
    }

    private static void announce(SableTrain train, String text) {
        String name = train.id().toString().substring(0, 8);
        if (train.level() == null) {
            return;
        }
        Component line = Component.literal("[loco] train " + name + ": " + text);
        for (ServerPlayer player : train.level().players()) {
            player.sendSystemMessage(line);
        }
    }

    private static double distanceToRed(SableTrain train, double direction, double range, boolean reserve) {
        RailCarriage carriage = train.car().carriage();
        TrackGraph graph = carriage == null ? null : carriage.graph();
        TravellingPoint origin = TrainStations.nosePoint(train);
        if (graph == null || origin == null || origin.edge == null) {
            return Double.NaN;
        }
        TravellingPoint scout = new TravellingPoint(
                origin.node1, origin.node2, origin.edge, origin.position, origin.upsideDown);
        SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
        SignalScan scan = new SignalScan(train, consistOf(train), range, reserve,
                runtime == null ? null : runtime.targetStation(),
                runtime != null && runtime.maneuver() ? runtime.maneuverFriends() : null);
        scout.travel(graph, direction * ConsistNose.frameFlip(train) * Math.max(range, CHAIN_REACH),
                routeSelector(runtime, scout.steer(SteerDirection.NONE, carriage.upNormal())),
                (distance, pair) -> scan.visit(Math.abs(distance), pair.getFirst(), pair.getSecond().getSecond()),
                scout.ignoreTurns(), scout.ignorePortals());
        scan.finish();
        return scan.stopAt;
    }

    private static ITrackSelector routeSelector(SableScheduleRuntime runtime, ITrackSelector fallback) {
        List<Couple<TrackNode>> live = runtime == null ? null : runtime.leadRail().remainingPath();
        if (live == null || live.isEmpty()) {
            return fallback;
        }
        List<Couple<TrackNode>> route = new ArrayList<>(live);
        return (graph, pair) -> {
            for (int i = 0; i < route.size(); i++) {
                TrackEdge hop = graph.getConnection(route.get(i));
                for (Map.Entry<TrackNode, TrackEdge> option : pair.getSecond()) {
                    if (option.getValue() == hop) {
                        route.subList(0, i + 1).clear();
                        return option;
                    }
                }
            }
            return fallback.apply(graph, pair);
        };
    }

    public static Set<UUID> consistOf(SableTrain train) {
        Set<UUID> ids = new HashSet<>();
        ids.add(train.id());
        UUID subLevel = train.car() == null ? null : train.car().subLevelId();
        if (train.level() == null || subLevel == null) {
            return ids;
        }
        for (UUID other : TrainConsist.connectedSubLevels(train.level(), subLevel)) {
            SableTrain car = SableTrainRegistry.bySubLevel(other);
            if (car != null) {
                ids.add(car.id());
            }
        }
        return ids;
    }

    public static boolean blockedFor(SignalEdgeGroup group, Set<UUID> consist) {
        return blocked(group, consist, new HashSet<>());
    }

    private static boolean blocked(SignalEdgeGroup group, Set<UUID> consist, Set<SignalEdgeGroup> visited) {
        if (!visited.add(group)) {
            return false;
        }
        if (group.reserved != null || group.trains.stream().anyMatch(other -> !consist.contains(other.id))
                || heldBySableTrain(group.id, consist)) {
            return true;
        }
        for (UUID crossing : group.intersecting.values()) {
            SignalEdgeGroup other = Create.RAILWAYS.signalEdgeGroups.get(crossing);
            if (other != null && blocked(other, consist, visited)) {
                return true;
            }
        }
        return false;
    }

    public static boolean heldBySableTrain(UUID groupId, Set<UUID> consist) {
        Set<UUID> occupants = OCCUPANTS.get(groupId);
        if (occupants != null && !consist.containsAll(occupants)) {
            return true;
        }
        Reservation reservation = reservationOf(groupId);
        return reservation != null && !consist.contains(reservation.train());
    }

    private static final class SignalScan {

        private final SableTrain train;
        private final Set<UUID> consist;
        private final double range;
        private final boolean reserve;
        private final GlobalStation destination;
        private final Set<UUID> former;
        private final Map<UUID, SignalBoundary> chain = new HashMap<>();
        private SignalBoundary chainStart;
        private double chainDistance;
        double stopAt = Double.NaN;
        int seen;

        SignalScan(SableTrain train, Set<UUID> consist, double range, boolean reserve, GlobalStation destination,
                   Set<UUID> former) {
            this.former = former;
            this.train = train;
            this.consist = consist;
            this.range = range;
            this.reserve = reserve;
            this.destination = destination;
        }

        boolean visit(double distance, TrackEdgePoint point, TrackNode ahead) {
            seen++;
            boolean tracking = chainStart != null;
            if (!tracking && distance > range) {
                return true;
            }
            if (point instanceof GlobalStation station && destination != null
                    && TrainStations.sameStation(station, destination) && station.canApproachFrom(ahead)) {
                return true;
            }
            if (!(point instanceof SignalBoundary signal)) {
                return false;
            }
            boolean side = signal.isPrimary(ahead);
            if (former != null) {
                return visitManeuver(distance, signal, side, ahead);
            }
            if (signal.blockEntities.get(side).isEmpty()) {
                return false;
            }
            UUID entering = signal.groups.get(side);
            SignalEdgeGroup group = entering == null ? null : Create.RAILWAYS.signalEdgeGroups.get(entering);
            if (group == null) {
                return false;
            }
            boolean cross = signal.types.get(side) == SignalType.CROSS_SIGNAL;
            boolean occupied = signal.isForcedRed(side) || blocked(group, consist, new HashSet<>());
            if (!tracking) {
                if (cross) {
                    chainStart = signal;
                    chainDistance = distance;
                    chain.put(entering, signal);
                }
                if (occupied) {
                    stopAt = distance;
                    return !cross;
                }
                if (!cross) {
                    claim(entering, signal);
                }
                return false;
            }
            chain.put(entering, signal);
            if (occupied) {
                stopAt = chainDistance;
                return !cross;
            }
            if (cross) {
                return false;
            }
            if (!Double.isNaN(stopAt)) {
                return true;
            }
            chainStart = null;
            claimChain();
            return false;
        }

        private boolean visitManeuver(double distance, SignalBoundary signal, boolean side, TrackNode ahead) {
            UUID entering = signal.groups.get(side);
            SignalEdgeGroup group = entering == null ? null : Create.RAILWAYS.signalEdgeGroups.get(entering);
            if (group == null) {
                return false;
            }
            String why = maneuverHold(signal, side, entering, group, ahead);
            if (why != null) {
                stopAt = distance;
                return true;
            }
            if (reserve) {
                RESERVED.put(entering, new Reservation(train.id(), signal.getId()));
            }
            return false;
        }

        private String maneuverHold(SignalBoundary signal, boolean side, UUID groupId, SignalEdgeGroup group, TrackNode ahead) {
            for (BlockPos pos : signal.blockEntities.get(side).keySet()) {
                if (TurntableSignals.forcedRed(train.level(), pos)) {
                    return "turntable plate holds the signal at " + pos.toShortString();
                }
            }
            Set<UUID> inside = new HashSet<>(occupantsOf(groupId));
            group.trains.forEach(other -> inside.add(other.id));
            Reservation reservation = reservationOf(groupId);
            UUID holder = reservation == null ? null : reservation.train();
            Set<UUID> foreign = new HashSet<>(inside);
            if (holder != null) {
                foreign.add(holder);
            }
            foreign.removeAll(consist);
            foreign.removeAll(former);
            if (foreign.isEmpty()) {
                return null;
            }
            Reservation earlier = PREVIOUS.get(groupId);
            UUID earlierHolder = earlier == null ? null : earlier.train();
            boolean selfHolds = earlierHolder != null && consist.contains(earlierHolder);
            for (UUID id : foreign) {
                boolean isInside = inside.contains(id);
                SableTrain other = SableTrainRegistry.get(id);
                if (isInside || other == null || !ManeuverPriority.isManeuvering(id)) {
                    return (isInside ? "train " : "reservation of train ") + ConsistNose.shortId(id)
                            + (ManeuverPriority.isManeuvering(id) ? " (maneuver)" : " (scheduled)") + " holds the group";
                }
                boolean otherHolds = id.equals(earlierHolder);
                if (ManeuverPriority.mustWait(train, other, otherHolds, selfHolds, ahead)) {
                    return "yields to maneuvering train " + ConsistNose.shortId(id);
                }
            }
            return null;
        }

        void finish() {
            if (chainStart != null && Double.isNaN(stopAt)) {
                claimChain();
            }
        }

        private void claimChain() {
            chain.forEach(this::claim);
            if (reserve) {
                Map<UUID, ChainClaim> held = CHAINS.computeIfAbsent(train.id(), key -> new HashMap<>());
                chain.forEach((groupId, signal) -> held.put(groupId,
                        new ChainClaim(signal.getId(), TrainStations.stationTickCounter)));
            }
            chain.clear();
        }

        private void claim(UUID groupId, SignalBoundary signal) {
            if (reserve) {
                RESERVED.put(groupId, new Reservation(train.id(), signal.getId()));
            }
        }
    }

    private TrainSignals() {
    }
}
