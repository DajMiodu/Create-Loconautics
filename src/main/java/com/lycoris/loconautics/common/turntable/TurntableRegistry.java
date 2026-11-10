package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TurntableRegistry {

    private static final double REACH = RailSeams.MAX_SEAM_GAP;
    private static final double RADIAL_DOT = 0.9;
    private static final double PLANE_TOLERANCE = 0.6;
    public static final double ALIGN_EPSILON = 0.75;
    public static final double FIT_SLACK = 0.5;
    private static final int HEAT_COOLDOWN_TICKS = 600;

    private static final int RESCAN_TICKS = 100;
    private static final int MERGE_INTERVAL = 20;
    private static final int MAX_PLATE_EDGES = 16;
    private static final int CLEARANCE_TICKS = 60;
    private static final int RESERVE_TICKS = 400;

    private static final Map<UUID, Turntable> TURNTABLES = new ConcurrentHashMap<>();
    private static final Map<UUID, Geometry> GEOMETRY = new ConcurrentHashMap<>();
    private static final Map<TrackNodeLocation, UUID> PLATE_HEADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> OCCUPIED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> RESERVED = new ConcurrentHashMap<>();
    private static final Map<UUID, Heat> HEAT = new ConcurrentHashMap<>();
    private static final Set<UUID> PLATES = ConcurrentHashMap.newKeySet();
    private static int tick;

    public record Exit(UUID plate, TrackNodeLocation entryHead, TrackNodeLocation movingHead,
                       TrackNodeLocation worldHead, double degrees, @Nullable TrackNodeLocation approachFrom) {

        public boolean entry() {
            return approachFrom != null;
        }
    }

    private record Heat(int count, int stamp) {
    }

    private record Geometry(int stamp, List<TrackNodeLocation> heads,
                            Map<TrackNodeLocation, List<TrackNodeLocation>> candidates, double span) {
    }

    private TurntableRegistry() {
    }

    public static void register(Turntable turntable) {
        UUID id = turntable.plateSubLevelId();
        if (id != null) {
            Turntable previous = TURNTABLES.put(id, turntable);
            if (previous != turntable) {
                TurntableTrace.log("register plate " + id.toString().substring(0, 8) + " "
                        + turntable.getClass().getSimpleName() + " replaces "
                        + (previous == null ? "nothing" : previous.getClass().getSimpleName()));
            }
        }
    }

    public static void dropGhost(GhostTurntable ghost) {
        TURNTABLES.remove(ghost.plateSubLevelId(), ghost);
    }

    public static void markPlate(@Nullable UUID plate) {
        if (plate != null) {
            PLATES.add(plate);
        }
    }

    public static void forgetPlate(@Nullable UUID plate) {
        if (plate != null) {
            PLATES.remove(plate);
        }
    }

    public static boolean anyPlate() {
        return !PLATES.isEmpty();
    }

    public static boolean isPlate(@Nullable UUID subLevel) {
        return subLevel != null && PLATES.contains(subLevel);
    }

    public static void unregister(@Nullable UUID plate) {
        if (plate == null) {
            return;
        }
        if (TURNTABLES.containsKey(plate)) {
            TurntableTrace.log("unregister plate " + plate.toString().substring(0, 8) + " via "
                    + TurntableTrace.callers());
        }
        TURNTABLES.remove(plate);
        GEOMETRY.remove(plate);
        PLATE_HEADS.values().removeIf(plate::equals);
        OCCUPIED.remove(plate);
        RESERVED.remove(plate);
    }

    public static void serverTick(MinecraftServer server) {
        tick++;
        TurntableGhosts.serverTick(server);
        trackOccupancy();
        if (tick % MERGE_INTERVAL == 0) {
            mergeExitNetworks();
        }
    }

    private static void trackOccupancy() {
        if (TURNTABLES.isEmpty()) {
            return;
        }
        for (SableTrain train : SableTrainRegistry.all()) {
            SableTrain.Car car = train.car();
            if (car == null || train.isDerailed()) {
                continue;
            }
            claim(car.carriage());
            for (SableTrain.Bogey bogey : car.bogeys()) {
                claim(bogey.rail());
            }
        }
    }

    private static void claim(@Nullable RailCarriage carriage) {
        for (UUID plate : TURNTABLES.keySet()) {
            if (hosts(plate, carriage)) {
                OCCUPIED.put(plate, tick);
            }
        }
    }

    public static boolean trainOnPlate(@Nullable UUID plate) {
        return plate != null && anyTrainOn(plate, null, false);
    }

    public static boolean consistOnPlate(UUID plate, Set<UUID> consist) {
        return anyTrainOn(plate, consist, true);
    }

    public static boolean otherTrainOnPlate(UUID plate, Set<UUID> consist) {
        return anyTrainOn(plate, consist, false);
    }

    // inside true counts only trains in the set, false counts every train but those
    private static boolean anyTrainOn(UUID plate, @Nullable Set<UUID> set, boolean inside) {
        for (SableTrain train : SableTrainRegistry.all()) {
            SableTrain.Car car = train.car();
            if (car == null || set != null && set.contains(train.id()) != inside) {
                continue;
            }
            if (hosts(plate, car.carriage())) {
                return true;
            }
            for (SableTrain.Bogey bogey : car.bogeys()) {
                if (hosts(plate, bogey.rail())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void noteServed(@Nullable UUID plate) {
        if (plate != null) {
            HEAT.put(plate, new Heat(heatOf(plate) + 1, tick));
        }
    }

    // turns served lately, one drops off every cooldown
    public static int heatOf(@Nullable UUID plate) {
        Heat heat = plate == null ? null : HEAT.get(plate);
        return heat == null ? 0 : Math.max(0, heat.count() - (tick - heat.stamp()) / HEAT_COOLDOWN_TICKS);
    }

    private static boolean hosts(UUID plate, @Nullable RailCarriage carriage) {
        return carriage != null && (touches(plate, carriage.leading()) || touches(plate, carriage.trailing()));
    }

    private static boolean touches(UUID plate, @Nullable TravellingPoint point) {
        TrackEdge edge = point == null ? null : point.edge;
        return edge != null && edge.node1 != null && edge.node2 != null
                && (hostedBy(plate, edge.node1.getLocation()) || hostedBy(plate, edge.node2.getLocation()));
    }

    public static boolean wholeTrainOn(UUID plate, SableTrain train) {
        SableTrain.Car car = train.car();
        if (car == null || car.subLevelId() == null || train.level() == null) {
            return false;
        }
        for (UUID member : TrainConsist.connectedSubLevels(train.level(), car.subLevelId())) {
            SableTrain other = SableTrainRegistry.bySubLevel(member);
            if (other != null && !wholeCarOn(plate, other.car())) {
                return false;
            }
        }
        return wholeCarOn(plate, car);
    }

    private static boolean wholeCarOn(UUID plate, @Nullable SableTrain.Car car) {
        if (car == null) {
            return false;
        }
        if (car.bogeys().isEmpty()) {
            return whollyOn(plate, car.carriage());
        }
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (!whollyOn(plate, bogey.rail())) {
                return false;
            }
        }
        return true;
    }

    private static boolean whollyOn(UUID plate, @Nullable RailCarriage rail) {
        return rail != null && wholly(plate, rail.leading()) && wholly(plate, rail.trailing());
    }

    private static boolean wholly(UUID plate, @Nullable TravellingPoint point) {
        TrackEdge edge = point == null ? null : point.edge;
        return edge != null && edge.node1 != null && edge.node2 != null
                && hostedBy(plate, edge.node1.getLocation()) && hostedBy(plate, edge.node2.getLocation());
    }

    public static boolean occupied(@Nullable UUID plate) {
        Integer last = plate == null ? null : OCCUPIED.get(plate);
        return last != null && tick - last <= CLEARANCE_TICKS;
    }

    // a scheduled train is on its way to this plate, so the plate must not swing home under it
    public static void reserve(@Nullable UUID plate) {
        if (plate != null) {
            RESERVED.put(plate, tick);
        }
    }

    public static boolean reserved(@Nullable UUID plate) {
        Integer last = plate == null ? null : RESERVED.get(plate);
        return last != null && tick - last <= RESERVE_TICKS;
    }

    public static void clear() {
        TURNTABLES.clear();
        GEOMETRY.clear();
        PLATE_HEADS.clear();
        OCCUPIED.clear();
        RESERVED.clear();
        HEAT.clear();
        PLATES.clear();
        TrackHostFrames.clear();
    }

    @Nullable
    public static Turntable byPlate(@Nullable UUID plate) {
        return plate == null ? null : TURNTABLES.get(plate);
    }

    public static java.util.List<Turntable> all() {
        return new ArrayList<>(TURNTABLES.values());
    }

    public static boolean anyRegistered() {
        return !TURNTABLES.isEmpty();
    }

    private static void mergeExitNetworks() {
        for (Turntable turntable : TURNTABLES.values()) {
            if (!turntable.turntableScheduleDriven()) {
                continue;
            }
            Geometry geometry = geometryOf(turntable);
            if (geometry == null) {
                continue;
            }
            TrackGraph plateGraph = graphHolding(geometry.heads().get(0));
            if (plateGraph == null) {
                continue;
            }
            for (List<TrackNodeLocation> reachable : geometry.candidates().values()) {
                for (TrackNodeLocation target : reachable) {
                    TrackGraph other = graphHolding(target);
                    if (other == null || other == plateGraph) {
                        continue;
                    }
                    plateGraph = RailSeams.mergeGraphs(plateGraph, other);
                }
            }
        }
    }

    public static List<Exit> exits(TrackGraph graph, TrackNode plateNode, @Nullable TrackNode cameFrom,
                                   double trainLength, boolean reversible) {
        if (graph == null || plateNode == null) {
            return List.of();
        }
        TrackNodeLocation reached = plateNode.getLocation();
        UUID plate = PLATE_HEADS.get(reached);
        Turntable turntable = byPlate(plate);
        if (turntable == null || !turntable.turntableScheduleDriven() || !turntable.turntablePowered()) {
            return List.of();
        }
        Geometry geometry = geometryOf(turntable);
        if (geometry == null || geometry.span() + FIT_SLACK < trainLength) {
            return List.of();
        }
        boolean alreadyOn = cameFrom != null && hostedBy(plate, cameFrom.getLocation());
        TrackNodeLocation front = alreadyOn ? reached : opposite(geometry, reached);
        TrackNodeLocation entryHead = front == null ? null : opposite(geometry, front);
        if (front == null) {
            return List.of();
        }
        List<Exit> exits = new ArrayList<>();
        addExits(exits, graph, turntable, plate, entryHead, front, null);
        if (reversible) {
            addExits(exits, graph, turntable, plate, entryHead, entryHead,
                    cameFrom == null ? null : cameFrom.getLocation());
        }
        return exits;
    }

    // why exits comes back empty for a plate head, null when it is not a plate head at all
    @Nullable
    public static String refusal(@Nullable TrackNode plateNode, double trainLength) {
        UUID plate = plateNode == null ? null : PLATE_HEADS.get(plateNode.getLocation());
        if (plate == null) {
            return null;
        }
        Turntable turntable = byPlate(plate);
        if (turntable == null) {
            return "the plate has no registered turntable";
        }
        if (!turntable.turntableScheduleDriven()) {
            return "the turntable is not schedule driven";
        }
        if (!turntable.turntablePowered()) {
            return "the turntable has no power";
        }
        Geometry geometry = geometryOf(turntable);
        if (geometry == null) {
            return "the plate does not have exactly two heads";
        }
        return geometry.span() + FIT_SLACK < trainLength
                ? "the plate span " + String.format("%.1f", geometry.span()) + " is shorter than the train "
                + String.format("%.1f", trainLength)
                : null;
    }

    private static void addExits(List<Exit> exits, TrackGraph graph, Turntable turntable, UUID plate,
                                 TrackNodeLocation entryHead, TrackNodeLocation moving,
                                 @Nullable TrackNodeLocation turnBack) {
        Geometry geometry = geometryOf(turntable);
        List<TrackNodeLocation> candidates = geometry == null ? null : geometry.candidates().get(moving);
        Vec3 axis = turntable.turntableAxisWorld();
        Vec3 normal = turntable.turntableAxisNormal();
        Vec3 from = RailSeams.worldPosOf(moving);
        if (candidates == null || axis == null || normal == null || from == null) {
            return;
        }
        for (TrackNodeLocation target : candidates) {
            Vec3 to = graph.locateNode(target) == null ? null : RailSeams.worldPosOf(target);
            if (to == null) {
                continue;
            }
            double degrees = Math.abs(rightHandedAngleDegrees(normal, from.subtract(axis), to.subtract(axis)));
            if (!target.equals(turnBack) && exitOpen(graph, moving, target)) {
                continue;
            }
            exits.add(new Exit(plate, entryHead, moving, target, degrees, null));
        }
    }

    public static List<Exit> entries(TrackGraph graph, TrackNode worldNode, @Nullable TrackNode cameFrom,
                                     double trainLength) {
        if (graph == null || worldNode == null || cameFrom == null || PLATE_HEADS.containsKey(worldNode.getLocation())) {
            return List.of();
        }
        TrackNodeLocation reached = worldNode.getLocation();
        List<Exit> entries = new ArrayList<>();
        for (Turntable turntable : TURNTABLES.values()) {
            if (!turntable.turntableScheduleDriven() || !turntable.turntablePowered()) {
                continue;
            }
            Geometry geometry = geometryOf(turntable);
            Vec3 axis = turntable.turntableAxisWorld();
            Vec3 normal = turntable.turntableAxisNormal();
            Vec3 to = RailSeams.worldPosOf(reached);
            if (geometry == null || geometry.span() + FIT_SLACK < trainLength || axis == null || normal == null || to == null) {
                continue;
            }
            for (TrackNodeLocation head : geometry.heads()) {
                List<TrackNodeLocation> reachable = geometry.candidates().get(head);
                Vec3 from = RailSeams.worldPosOf(head);
                if (reachable == null || !reachable.contains(reached) || from == null
                        || graph.locateNode(head) == null || exitOpen(graph, head, reached)) {
                    continue;
                }
                double degrees = Math.abs(rightHandedAngleDegrees(normal, from.subtract(axis), to.subtract(axis)));
                entries.add(new Exit(turntable.plateSubLevelId(), head, head, reached, degrees,
                        cameFrom.getLocation()));
            }
        }
        return entries;
    }

    // the track across a plate is a chain, not always one edge, a node where two track blocks meet splits it
    @Nullable
    public static List<Couple<TrackNode>> plateRouteFrom(TrackGraph graph, UUID plate, TrackNodeLocation arrivedAt) {
        Geometry geometry = geometryOf(byPlate(plate));
        TrackNodeLocation other = geometry == null ? null : opposite(geometry, arrivedAt);
        TrackNode node = other == null ? null : graph.locateNode(arrivedAt);
        TrackNode goal = other == null ? null : graph.locateNode(other);
        if (node == null || goal == null) {
            return null;
        }
        List<Couple<TrackNode>> route = new ArrayList<>();
        while (node != goal) {
            TrackNode previous = route.isEmpty() ? null : route.get(route.size() - 1).getFirst();
            TrackNode next = nextOnPlate(graph, plate, node, previous);
            if (next == null || route.size() >= MAX_PLATE_EDGES) {
                return null;
            }
            route.add(Couple.create(node, next));
            node = next;
        }
        return route;
    }

    @Nullable
    private static TrackNode nextOnPlate(TrackGraph graph, UUID plate, TrackNode node, @Nullable TrackNode previous) {
        for (TrackNode candidate : graph.getConnectionsFrom(node).keySet()) {
            if (candidate != previous && hostedBy(plate, candidate.getLocation())) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hostedBy(UUID plate, TrackNodeLocation location) {
        return plate.equals(RailSeams.hostIdOf(location));
    }

    @Nullable
    public static TrackGraphLocation centerOf(TrackGraph graph, List<Couple<TrackNode>> plateRoute) {
        double total = 0.0;
        for (Couple<TrackNode> edge : plateRoute) {
            TrackEdge connection = graph.getConnection(edge);
            if (connection == null) {
                return null;
            }
            total += connection.getLength();
        }
        double half = total * 0.5;
        double walked = 0.0;
        for (Couple<TrackNode> edge : plateRoute) {
            double length = graph.getConnection(edge).getLength();
            if (walked + length >= half) {
                TrackGraphLocation location = new TrackGraphLocation();
                location.graph = graph;
                location.edge = Couple.create(edge.getFirst().getLocation(), edge.getSecond().getLocation());
                location.position = half - walked;
                return location;
            }
            walked += length;
        }
        return null;
    }

    public static void unweldPlateSeams(@Nullable UUID plate) {
        Geometry geometry = geometryOf(byPlate(plate));
        if (geometry == null) {
            return;
        }
        for (TrackNodeLocation head : geometry.heads()) {
            RailSeams.unweldSeamsAt(head);
        }
    }

    // how far the middle of the whole body sits from the plate center along the plate, for the aligning trace
    public static double spanOf(UUID plate) {
        Geometry geometry = geometryOf(byPlate(plate));
        return geometry == null ? 0.0 : geometry.span();
    }

    public static double alignmentError(Turntable turntable, TrackNodeLocation movingHead,
                                        TrackNodeLocation target) {
        Vec3 axis = turntable.turntableAxisWorld();
        Vec3 normal = turntable.turntableAxisNormal();
        Vec3 from = RailSeams.worldPosOf(movingHead);
        Vec3 to = RailSeams.worldPosOf(target);
        if (axis == null || normal == null || from == null || to == null) {
            return 0.0;
        }
        return rightHandedAngleDegrees(normal, from.subtract(axis), to.subtract(axis));
    }

    public static double rightHandedAngleDegrees(Vec3 normal, Vec3 a, Vec3 b) {
        Vec3 flatA = a.subtract(normal.scale(a.dot(normal)));
        Vec3 flatB = b.subtract(normal.scale(b.dot(normal)));
        if (flatA.lengthSqr() < 1.0e-6 || flatB.lengthSqr() < 1.0e-6) {
            return 0.0;
        }
        flatA = flatA.normalize();
        flatB = flatB.normalize();
        double cos = Mth.clamp(flatA.dot(flatB), -1.0, 1.0);
        double sin = flatA.cross(flatB).dot(normal);
        return Math.toDegrees(Math.atan2(sin, cos));
    }

    public static boolean weldAligned(Turntable turntable, TrackNodeLocation movingHead,
                                      TrackNodeLocation target) {
        boolean aligned = RailSeams.aligned(movingHead, target);
        boolean welded = aligned && RailSeams.weldNow(movingHead, target);
        if (!welded && TurntableTrace.due("weld" + movingHead, tick, 20)) {
            Vec3 a = RailSeams.worldPosOf(movingHead);
            Vec3 b = RailSeams.worldPosOf(target);
            TurntableTrace.log("weld refused aligned=" + aligned + " gap="
                    + (a == null || b == null ? "none" : String.format("%.3f", a.distanceTo(b))) + " moving=" + a
                    + " target=" + b);
        }
        return welded;
    }

    public static boolean exitOpen(TrackGraph graph, TrackNodeLocation movingHead, TrackNodeLocation target) {
        TrackNode a = graph == null ? null : graph.locateNode(movingHead);
        TrackNode b = graph == null ? null : graph.locateNode(target);
        return a != null && b != null
                && graph.getConnection(Couple.create(a, b)) != null
                && RailSeams.aligned(movingHead, target);
    }

    @Nullable
    public static TrackGraph graphHolding(@Nullable TrackNodeLocation location) {
        Map<UUID, TrackGraph> networks = Create.RAILWAYS == null ? null : Create.RAILWAYS.trackNetworks;
        if (location == null || networks == null) {
            return null;
        }
        for (TrackGraph graph : new ArrayList<>(networks.values())) {
            if (graph.locateNode(location) != null) {
                return graph;
            }
        }
        return null;
    }

    @Nullable
    private static TrackNodeLocation opposite(Geometry geometry, TrackNodeLocation head) {
        for (TrackNodeLocation other : geometry.heads()) {
            if (!other.equals(head)) {
                return other;
            }
        }
        return null;
    }

    @Nullable
    private static Geometry geometryOf(@Nullable Turntable turntable) {
        UUID plate = turntable == null ? null : turntable.plateSubLevelId();
        if (plate == null) {
            return null;
        }
        Geometry cached = GEOMETRY.get(plate);
        if (cached == null || tick - cached.stamp() >= RESCAN_TICKS) {
            cached = scan(turntable);
            Geometry before = GEOMETRY.put(plate, cached);
            if (before == null || before.heads().size() != cached.heads().size()) {
                TurntableTrace.log("plate " + plate.toString().substring(0, 8) + " scan heads "
                        + (before == null ? "first" : before.heads().size()) + " to " + cached.heads().size()
                        + " span " + String.format("%.2f", cached.span()) + " axis " + turntable.turntableAxisWorld()
                        + " impl " + turntable.getClass().getSimpleName());
            }
            PLATE_HEADS.values().removeIf(plate::equals);
            for (TrackNodeLocation head : cached.heads()) {
                PLATE_HEADS.put(head, plate);
            }
        }
        return cached.heads().size() == 2 ? cached : null;
    }

    private static Geometry scan(Turntable turntable) {
        UUID plate = turntable.plateSubLevelId();
        Vec3 axis = turntable.turntableAxisWorld();
        Vec3 normal = turntable.turntableAxisNormal();
        Map<UUID, TrackGraph> networks = Create.RAILWAYS == null ? null : Create.RAILWAYS.trackNetworks;
        if (networks == null || axis == null || normal == null) {
            return new Geometry(tick, List.of(), Map.of(), 0.0);
        }

        List<TrackNodeLocation> heads = new ArrayList<>(2);
        List<RailSeams.Railhead> worldHeads = new ArrayList<>();
        for (TrackGraph graph : new ArrayList<>(networks.values())) {
            for (TrackNodeLocation location : new ArrayList<>(graph.getNodes())) {
                RailSeams.Railhead head = RailSeams.railheadOf(graph, graph.locateNode(location));
                if (head == null) {
                    continue;
                }
                UUID host = RailSeams.hostIdOf(location);
                if (host == null) {
                    worldHeads.add(head);
                } else if (plate.equals(host)) {
                    heads.add(location);
                }
            }
        }
        if (heads.size() != 2) {
            return new Geometry(tick, List.of(), Map.of(), 0.0);
        }

        Vec3 endA = RailSeams.worldPosOf(heads.get(0));
        Vec3 endB = RailSeams.worldPosOf(heads.get(1));
        if (endA == null || endB == null) {
            return new Geometry(tick, List.of(), Map.of(), 0.0);
        }

        Map<TrackNodeLocation, List<TrackNodeLocation>> candidates = new ConcurrentHashMap<>();
        for (TrackNodeLocation head : heads) {
            Vec3 fromAxis = RailSeams.worldPosOf(head).subtract(axis);
            double radius = flatLength(normal, fromAxis);
            double height = fromAxis.dot(normal);
            List<TrackNodeLocation> reachable = new ArrayList<>();
            for (RailSeams.Railhead world : worldHeads) {
                if (reaches(axis, normal, radius, height, world)) {
                    reachable.add(world.node().getLocation());
                }
            }
            candidates.put(head, Collections.unmodifiableList(reachable));
        }
        return new Geometry(tick, List.copyOf(heads), candidates, endA.distanceTo(endB));
    }

    private static boolean reaches(Vec3 axis, Vec3 normal, double radius, double height, RailSeams.Railhead world) {
        Vec3 toWorld = world.world().subtract(axis);
        if (Math.abs(toWorld.dot(normal) - height) > PLANE_TOLERANCE
                || Math.abs(flatLength(normal, toWorld) - radius) > REACH) {
            return false;
        }
        Vec3 radial = toWorld.subtract(normal.scale(toWorld.dot(normal)));
        return radial.lengthSqr() >= 1.0e-6 && world.alongRail().dot(radial.normalize()) >= RADIAL_DOT;
    }

    private static double flatLength(Vec3 normal, Vec3 v) {
        return v.subtract(normal.scale(v.dot(normal))).length();
    }
}
