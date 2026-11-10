package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.common.railway.PointOverrides;
import com.lycoris.loconautics.common.railway.SignalOverrides;
import com.lycoris.loconautics.common.turntable.Turntable;
import com.lycoris.loconautics.common.turntable.GhostTurntable;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.signal.TrainSignalAccessor.SignalAhead;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.station.GlobalStation;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Glob;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CcNetwork {

    static final int TRACK_NODE_LIMIT = 2000;

    public record Fleet(List<SableTrain> trains, Map<UUID, UUID> ownerOfCar) {
    }

    private record Placed(String dimension, Vec3 world, boolean sub) {
    }

    private record Region(String dimension, boolean subOnly, double minX, double minY, double minZ,
                          double maxX, double maxY, double maxZ) {

        boolean contains(String dim, Vec3 p) {
            return (dimension == null || dimension.equals(dim))
                    && p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY && p.z >= minZ && p.z <= maxZ;
        }
    }

    private CcNetwork() {
    }

    private static ServerLevel levelOf(ResourceKey<Level> dimension) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(dimension);
    }

    private static Placed place(ResourceKey<Level> dimension, Vec3 point) {
        ServerLevel level = levelOf(dimension);
        String name = dimension.location().toString();
        if (level == null) {
            return new Placed(name, point, false);
        }
        boolean sub = Sable.HELPER.getContaining(level, BlockPos.containing(point)) != null;
        return new Placed(name, sub ? Sable.HELPER.projectOutOfSubLevel(level, point) : point, sub);
    }

    static void addPosition(Map<String, Object> entry, ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return;
        }
        Placed placed = place(dimension, Vec3.atCenterOf(pos));
        entry.put("dimension", placed.dimension());
        entry.put("x", placed.world().x);
        entry.put("y", placed.world().y);
        entry.put("z", placed.world().z);
        entry.put("sublevel", placed.sub());
    }

    private static Placed place(TrackNodeLocation location) {
        return place(location.getDimension(), location.getLocation());
    }

    public static Fleet fleet() {
        List<SableTrain> trains = new ArrayList<>();
        Map<UUID, UUID> owner = new HashMap<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.car() == null || owner.containsKey(train.id())) {
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            if (sub == null || BearingAxleValidity.resolve(sub) == null) {
                continue;
            }
            trains.add(train);
            for (UUID car : TrainSignals.consistOf(train)) {
                owner.put(car, train.id());
            }
        }
        return new Fleet(trains, owner);
    }

    private static Set<UUID> trainsOf(Set<UUID> cars, Map<UUID, UUID> owner) {
        Set<UUID> out = new HashSet<>();
        for (UUID car : cars) {
            UUID train = owner.get(car);
            if (train != null) {
                out.add(train);
            }
        }
        return out;
    }

    private static List<String> strings(Set<UUID> ids) {
        List<String> out = new ArrayList<>();
        for (UUID id : ids) {
            out.add(id.toString());
        }
        return out;
    }

    public static List<Map<String, Object>> trains(String filter) {
        String regex = Glob.toRegexPattern(filter, "");
        Fleet fleet = fleet();
        List<Map<String, Object>> out = new ArrayList<>();
        for (SableTrain train : fleet.trains()) {
            String name = SableTrainSpawner.trainName(train.level(), train);
            if (!name.matches(regex)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", train.id().toString());
            entry.put("name", name);
            entry.put("dimension", train.level().dimension().location().toString());
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            if (sub != null) {
                Vector3d pos = SubLevelData.position(sub);
                entry.put("x", pos.x);
                entry.put("y", pos.y);
                entry.put("z", pos.z);
            }
            entry.put("speed", train.speed());
            entry.put("derailed", train.isDerailed());
            entry.put("stationState", train.stationState().name().toLowerCase());
            GlobalStation here = train.atStation() != null ? train.atStation() : train.currentStation();
            if (here != null) {
                entry.put("station", here.name);
            }
            SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
            GlobalStation target = runtime == null ? null : runtime.targetStation();
            if (target != null) {
                entry.put("target", target.name);
            }
            if (runtime != null) {
                double remaining = runtime.remainingToTarget();
                if (remaining >= 0.0) {
                    entry.put("remaining", remaining);
                }
                entry.put("scheduleEntry", runtime.currentEntry + 1);
                entry.put("scheduleSize", runtime.schedule == null ? 0 : runtime.schedule.entries.size());
                entry.put("paused", runtime.paused);
                String reason = runtime.heldReason();
                if (reason != null) {
                    entry.put("heldReason", reason);
                }
            }
            entry.put("heldAtRed", TrainSignals.isHeld(train.id()));
            entry.put("carriages", ConsistAccess.carriages(train.level(), train.car().subLevelId()).size());
            entry.put("length", TrainLength.consistLength(train.level(), train.car().subLevelId()));
            entry.put("width", TrainLength.consistWidth(train.level(), train.car().subLevelId()));
            entry.put("height", TrainLength.consistHeight(train.level(), train.car().subLevelId()));
            entry.put("dwellTicks", train.dwellTicks());
            Set<UUID> blocks = new HashSet<>();
            for (UUID car : TrainSignals.consistOf(train)) {
                blocks.addAll(TrainSignals.groupsOf(car));
            }
            entry.put("blocks", strings(blocks));
            out.add(entry);
        }
        return out;
    }

    public static List<Map<String, Object>> signals(IArguments args) throws LuaException {
        Region region = region(args, 0, false);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TrackGraph graph : graphs()) {
            for (SignalBoundary signal : graph.getPoints(EdgePointType.SIGNAL)) {
                Map<String, Object> entry = signalEntry(signal);
                if (entry == null) {
                    continue;
                }
                if (region != null && !region.contains((String) entry.get("dimension"),
                        new Vec3((double) entry.get("x"), (double) entry.get("y"), (double) entry.get("z")))) {
                    continue;
                }
                out.add(entry);
            }
        }
        return out;
    }

    private static Map<String, Object> signalEntry(SignalBoundary signal) {
        List<Map<String, Object>> sides = new ArrayList<>();
        BlockPos first = null;
        for (boolean primary : new boolean[] { true, false }) {
            Map<String, Object> side = sideInfo(signal, primary);
            if (side == null) {
                continue;
            }
            if (first == null) {
                first = signal.blockEntities.get(primary).keySet().iterator().next();
            }
            sides.add(side);
        }
        if (first == null) {
            return null;
        }
        Placed placed = place(signal.edgeLocation.getFirst().getDimension(), Vec3.atCenterOf(first));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", signal.getId().toString());
        entry.put("dimension", placed.dimension());
        entry.put("x", placed.world().x);
        entry.put("y", placed.world().y);
        entry.put("z", placed.world().z);
        entry.put("sublevel", placed.sub());
        entry.put("sides", sides);
        return entry;
    }

    public static Map<String, Object> sideInfo(SignalBoundary signal, boolean primary) {
        Map<BlockPos, Boolean> blocks = signal.blockEntities.get(primary);
        if (blocks.isEmpty()) {
            return null;
        }
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("side", primary ? 1 : 2);
        side.put("type", signal.types.get(primary) == SignalType.CROSS_SIGNAL ? "chain" : "block");
        side.put("state", stateOf(signal, primary));
        side.put("forcedRed", signal.isForcedRed(primary));
        side.put("held", SignalOverrides.held(signal.getId(), primary));
        UUID group = signal.groups.get(primary);
        if (group != null) {
            side.put("block", group.toString());
        }
        return side;
    }

    public static String stateOf(SignalBoundary signal, boolean primary) {
        var state = signal.cachedStates.get(primary);
        return state == null ? "invalid" : state.name().toLowerCase();
    }

    public static Map<String, Object> nextSignal(SignalAhead ahead) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", ahead.signal().getId().toString());
        entry.put("distance", ahead.distance());
        entry.put("state", stateOf(ahead.signal(), ahead.primary()));
        entry.put("type", ahead.signal().types.get(ahead.primary()) == SignalType.CROSS_SIGNAL ? "chain" : "block");
        UUID group = ahead.signal().groups.get(ahead.primary());
        if (group != null) {
            entry.put("block", group.toString());
        }
        return entry;
    }

    public static List<Map<String, Object>> blocks(boolean busyOnly) {
        Fleet fleet = fleet();
        Map<UUID, List<String>> bounding = new HashMap<>();
        for (TrackGraph graph : graphs()) {
            for (SignalBoundary signal : graph.getPoints(EdgePointType.SIGNAL)) {
                for (boolean primary : new boolean[] { true, false }) {
                    UUID group = signal.groups.get(primary);
                    if (group != null && !signal.blockEntities.get(primary).isEmpty()) {
                        bounding.computeIfAbsent(group, key -> new ArrayList<>()).add(signal.getId().toString());
                    }
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (SignalEdgeGroup group : new ArrayList<>(Create.RAILWAYS.signalEdgeGroups.values())) {
            List<String> signals = bounding.get(group.id);
            if (signals == null) {
                continue;
            }
            Set<UUID> occupants = trainsOf(TrainSignals.occupantsOf(group.id), fleet.ownerOfCar());
            UUID reserved = fleet.ownerOfCar().get(TrainSignals.reservedBy(group.id));
            if (busyOnly && occupants.isEmpty() && reserved == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", group.id.toString());
            entry.put("occupiedBy", strings(occupants));
            if (reserved != null) {
                entry.put("reservedBy", reserved.toString());
            }
            entry.put("signals", signals);
            out.add(entry);
        }
        return out;
    }

    public static List<Map<String, Object>> junctions(IArguments args) throws LuaException {
        Region region = region(args, 0, false);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TrackGraph graph : graphs()) {
            for (TrackNodeLocation location : graph.getNodes()) {
                TrackNode node = graph.locateNode(location);
                Map<TrackNode, TrackEdge> connections = node == null ? null : graph.getConnectionsFrom(node);
                if (connections == null || connections.size() < 3) {
                    continue;
                }
                Placed placed = place(location);
                if (region != null && !region.contains(placed.dimension(), placed.world())) {
                    continue;
                }
                Map<String, Object> entry = nodeEntry(location, placed);
                List<Map<String, Object>> edges = new ArrayList<>();
                for (Map.Entry<TrackNode, TrackEdge> connection : connections.entrySet()) {
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("to", nodeId(connection.getKey().getLocation()));
                    edge.put("length", connection.getValue().getLength());
                    edges.add(edge);
                }
                entry.put("edges", edges);
                out.add(entry);
            }
        }
        return out;
    }

    public static Map<String, Object> track(IArguments args) throws LuaException {
        Region region = region(args, 0, true);
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        for (TrackGraph graph : graphs()) {
            for (TrackNodeLocation location : graph.getNodes()) {
                TrackNode node = graph.locateNode(location);
                if (node == null) {
                    continue;
                }
                Placed placed = place(location);
                boolean inside = region.contains(placed.dimension(), placed.world())
                        && (!region.subOnly() || placed.sub());
                for (Map.Entry<TrackNode, TrackEdge> connection : graph.getConnectionsFrom(node).entrySet()) {
                    TrackNodeLocation other = connection.getKey().getLocation();
                    if (nodeId(location).compareTo(nodeId(other)) > 0) {
                        continue;
                    }
                    Placed otherPlaced = place(other);
                    boolean otherInside = region.contains(otherPlaced.dimension(), otherPlaced.world());
                    boolean sub = placed.sub() || otherPlaced.sub();
                    if ((!inside && !otherInside) || (region.subOnly() && !sub)) {
                        continue;
                    }
                    TrackEdge edge = connection.getValue();
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("from", nodeId(location));
                    entry.put("to", nodeId(other));
                    entry.put("length", edge.getLength());
                    entry.put("curve", edge.isTurn());
                    entry.put("portal", edge.isInterDimensional());
                    entry.put("sublevel", sub);
                    if (edge.isTurn() && !edge.isInterDimensional()) {
                        entry.put("path", curvePath(graph, edge, location.getDimension()));
                    }
                    edges.add(entry);
                }
                if (inside) {
                    if (nodes.size() >= TRACK_NODE_LIMIT) {
                        throw new LuaException("region holds more than " + TRACK_NODE_LIMIT
                                + " track nodes, ask for a smaller box");
                    }
                    nodes.add(nodeEntry(location, placed));
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", nodes);
        out.put("edges", edges);
        return out;
    }

    private static List<Double> curvePath(TrackGraph graph, TrackEdge edge, ResourceKey<Level> dimension) {
        int segments = Math.max(2, edge.getTurn().getSegmentCount());
        List<Double> path = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            Vec3 world = place(dimension, edge.getPosition(graph, (double) i / segments)).world();
            path.add(world.x);
            path.add(world.z);
        }
        return path;
    }

    public static List<Map<String, Object>> turntables(IArguments args) throws LuaException {
        Region region = region(args, 0, false);
        List<Map<String, Object>> out = new ArrayList<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return out;
        }
        for (Turntable turntable : TurntableRegistry.all()) {
            UUID plate = turntable.plateSubLevelId();
            ServerLevel home = turntable instanceof GhostTurntable ghost ? server.getLevel(ghost.dimension()) : null;
            for (ServerLevel level : server.getAllLevels()) {
                if (home == null && SubLevelData.resolve(level, plate) != null) {
                    home = level;
                    break;
                }
            }
            if (home == null) {
                continue;
            }
            Vec3 axis = turntable.turntableAxisWorld();
            String dimension = home.dimension().location().toString();
            if (region != null && !region.contains(dimension, axis)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", plate.toString());
            entry.put("dimension", dimension);
            entry.put("x", axis.x);
            entry.put("y", axis.y);
            entry.put("z", axis.z);
            entry.put("powered", turntable.turntablePowered());
            entry.put("settled", turntable.turntableSettled());
            entry.put("aligned", turntable.turntableAligned());
            entry.put("occupied", TurntableRegistry.occupied(plate));
            out.add(entry);
        }
        return out;
    }

    private static Map<String, Object> nodeEntry(TrackNodeLocation location, Placed placed) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", nodeId(location));
        entry.put("dimension", placed.dimension());
        entry.put("x", placed.world().x);
        entry.put("y", placed.world().y);
        entry.put("z", placed.world().z);
        entry.put("sublevel", placed.sub());
        return entry;
    }

    private static String nodeId(TrackNodeLocation location) {
        return PointOverrides.nodeId(location);
    }

    public static SignalBoundary findSignal(UUID id) {
        for (TrackGraph graph : graphs()) {
            SignalBoundary signal = graph.getPoint(EdgePointType.SIGNAL, id);
            if (signal != null) {
                return signal;
            }
        }
        return null;
    }

    public static void requireJunctionTo(String junction, String toward) throws LuaException {
        for (TrackGraph graph : graphs()) {
            for (TrackNodeLocation location : graph.getNodes()) {
                if (!nodeId(location).equals(junction)) {
                    continue;
                }
                TrackNode node = graph.locateNode(location);
                Map<TrackNode, TrackEdge> connections = node == null ? Map.of() : graph.getConnectionsFrom(node);
                if (connections.size() < 3) {
                    throw new LuaException("that node is not a junction, list them with getJunctions");
                }
                for (TrackNode next : connections.keySet()) {
                    if (nodeId(next.getLocation()).equals(toward)) {
                        return;
                    }
                }
                throw new LuaException("no track leads from that junction to the other node");
            }
        }
        throw new LuaException("no track node with that id, list junctions with getJunctions");
    }

    public static String trainName(SableTrain train) {
        return SableTrainSpawner.trainName(train.level(), train);
    }

    private static List<TrackGraph> graphs() {
        if (Create.RAILWAYS == null) {
            return List.of();
        }
        return new ArrayList<>(Create.RAILWAYS.trackNetworks.values());
    }

    private static Region region(IArguments args, int index, boolean required) throws LuaException {
        Object raw = args.get(index);
        if (raw == null) {
            if (required) {
                throw new LuaException("a region table is needed, {minX=, minY=, minZ=, maxX=, maxY=, maxZ=}");
            }
            return null;
        }
        if (!(raw instanceof Map<?, ?> table)) {
            throw new LuaException("bad argument #" + (index + 1) + " (table expected, got " + raw.getClass().getSimpleName() + ")");
        }
        Object dimension = table.get("dimension");
        return new Region(dimension == null ? null : dimension.toString(), Boolean.TRUE.equals(table.get("subOnly")),
                bound(table, "minX", Double.NEGATIVE_INFINITY), bound(table, "minY", Double.NEGATIVE_INFINITY),
                bound(table, "minZ", Double.NEGATIVE_INFINITY), bound(table, "maxX", Double.POSITIVE_INFINITY),
                bound(table, "maxY", Double.POSITIVE_INFINITY), bound(table, "maxZ", Double.POSITIVE_INFINITY));
    }

    private static double bound(Map<?, ?> table, String key, double fallback) throws LuaException {
        Object value = table.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new LuaException(key + " must be a number");
    }
}
