package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.Create;
import com.simibubi.create.api.event.TrackGraphMergeEvent;
import com.lycoris.loconautics.common.turntable.Turntable;
import com.lycoris.loconautics.common.sable.TrackHostFramesClient;
import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RailSeams {

    public static final double MAX_SEAM_GAP = 1.0;
    // never zero, a zero length edge takes creates portal branch and spends no distance
    private static final double MIN_SEAM_LENGTH = 1.0e-3;

    private static final Map<ResourceKey<Level>, Level> CLIENT_LEVELS = new ConcurrentHashMap<>();

    private static final int SWEEP_INTERVAL = 20;
    private static final int MAX_WELDS_PER_SWEEP = 8;
    private static final double FACING_DOT = -0.9;

    private static final ThreadLocal<Boolean> CREATE_TRAIN_TRAVEL = new ThreadLocal<>();

    private static int sweepTicks;

    private RailSeams() {
    }

    public static void beginCreateTrainTravel() {
        CREATE_TRAIN_TRAVEL.set(Boolean.TRUE);
    }

    public static void endCreateTrainTravel() {
        CREATE_TRAIN_TRAVEL.remove();
    }

    public static boolean createTrainTravelling() {
        return CREATE_TRAIN_TRAVEL.get() != null;
    }

    public static void trackClientLevel(Level level) {
        if (level != null && level.isClientSide()) {
            CLIENT_LEVELS.put(level.dimension(), level);
        }
    }

    public static boolean isSeam(TrackNodeLocation a, TrackNodeLocation b) {
        return Boolean.TRUE.equals(seamState(a, b));
    }

    // sub level rails share the worlds dimension key, so creates own interdimensional test cannot see a seam. null means no container yet, which is not the same answer as not a seam and must not be cached
    public static Boolean seamState(TrackNodeLocation a, TrackNodeLocation b) {
        if (a == null || b == null || a.dimension == null || !a.dimension.equals(b.dimension)) {
            return Boolean.FALSE;
        }
        SubLevelContainer container = anyContainer(a.dimension);
        if (container == null) {
            return null;
        }
        boolean plotA = inPlotGrid(container, a);
        boolean plotB = inPlotGrid(container, b);
        if (plotA != plotB) {
            return Boolean.TRUE;
        }
        if (!plotA) {
            return Boolean.FALSE;
        }
        ChunkPos cellA = plotCellOf(container, a);
        ChunkPos cellB = plotCellOf(container, b);
        if (cellA == null || cellB == null) {
            return null;
        }
        return !cellA.equals(cellB);
    }

    private static ChunkPos plotCellOf(SubLevelContainer container, TrackNodeLocation location) {
        Vec3 v = location.getLocation();
        LevelPlot plot = container.getPlot(new ChunkPos(BlockPos.containing(v.x, v.y, v.z)));
        return plot == null ? null : plot.plotPos;
    }

    public static boolean isSeam(Couple<TrackNodeLocation> crossing) {
        return crossing != null && isSeam(crossing.getFirst(), crossing.getSecond());
    }

    public static boolean crossable(Couple<TrackNodeLocation> crossing) {
        if (crossing == null) {
            return false;
        }
        TrackNodeLocation a = crossing.getFirst();
        TrackNodeLocation b = crossing.getSecond();
        return isSeam(a, b) && aligned(a, b);
    }

    public static boolean aligned(TrackNodeLocation a, TrackNodeLocation b) {
        Vec3 pa = worldPosOf(a);
        Vec3 pb = worldPosOf(b);
        return pa != null && pb != null && pa.distanceToSqr(pb) <= MAX_SEAM_GAP * MAX_SEAM_GAP;
    }

    public static ServerSubLevel serverHostOf(TrackNodeLocation location) {
        return location == null ? null
                : hostIn(serverLevel(location.dimension), location) instanceof ServerSubLevel sub ? sub : null;
    }

    public static SubLevel clientHostOf(TrackNodeLocation location) {
        return location == null ? null : hostIn(CLIENT_LEVELS.get(location.dimension), location);
    }

    public static Vec3 worldPosOf(TrackNodeLocation location) {
        return location == null ? null : worldPosIn(poseOf(location, true), location);
    }

    public static Pose3dc serverPoseOf(TrackNodeLocation location) {
        return location == null ? null : poseOf(location, true);
    }

    public static boolean hostBodyMissing(TrackNodeLocation location) {
        return location != null && inPlotGrid(location) && serverHostOf(location) == null;
    }

    public static UUID hostIdOf(TrackNodeLocation location) {
        ServerSubLevel host = serverHostOf(location);
        if (host != null) {
            return host.getUniqueId();
        }
        return inPlotGrid(location) ? TrackHostFrames.hostId(location) : null;
    }

    // the loaded body, or the last pose kept for it
    private static Pose3dc poseOf(TrackNodeLocation location, boolean server) {
        SubLevel host = server ? serverHostOf(location) : clientHostOf(location);
        if (host != null) {
            return host.logicalPose();
        }
        if (!inPlotGrid(location)) {
            return null;
        }
        return server ? TrackHostFrames.poseAt(location) : TrackHostFramesClient.poseAt(location);
    }

    private static Vec3 worldPosIn(Pose3dc pose, TrackNodeLocation location) {
        Vec3 v = location.getLocation();
        if (pose == null) {
            return v;
        }
        Vector3d w = pose.transformPosition(new Vector3d(v.x, v.y, v.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    public static Pose3dc clientPoseOf(TrackNodeLocation location) {
        return location == null ? null : poseOf(location, false);
    }

    public static Vec3 worldPointOf(TrackNodeLocation frame, Vec3 point) {
        Pose3dc pose = frame == null || point == null ? null : poseOf(frame, true);
        if (pose == null) {
            return point;
        }
        Vector3d w = pose.transformPosition(new Vector3d(point.x, point.y, point.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    public static boolean inPlotGrid(TrackNodeLocation location) {
        SubLevelContainer container = location == null ? null : anyContainer(location.dimension);
        return container != null && inPlotGrid(container, location);
    }

    private static boolean serverSide() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread();
    }

    public static double seamLength(TrackNodeLocation a, TrackNodeLocation b) {
        boolean server = serverSide();
        Vec3 pa = worldPosIn(poseOf(a, server), a);
        Vec3 pb = worldPosIn(poseOf(b, server), b);
        double gap = pa.distanceTo(pb);
        // an unloaded host reads in plot coordinates, and walking a bridge thousands of blocks long drags the car off
        return Double.isFinite(gap) && gap <= MAX_SEAM_GAP ? Math.max(gap, MIN_SEAM_LENGTH) : MIN_SEAM_LENGTH;
    }

    // expressed in the first nodes frame, since every caller lifts a point through the host of its edges node1
    public static Vec3 seamPoint(TrackNodeLocation a, TrackNodeLocation b, double t) {
        boolean server = serverSide();
        Pose3dc poseA = poseOf(a, server);
        Vec3 pa = worldPosIn(poseA, a);
        Vec3 pb = worldPosIn(poseOf(b, server), b);
        if (pa.distanceToSqr(pb) > MAX_SEAM_GAP * MAX_SEAM_GAP) {
            return a.getLocation();
        }
        Vec3 w = pa.lerp(pb, Mth.clamp(t, 0.0, 1.0));
        if (poseA == null) {
            return w;
        }
        Vector3d l = poseA.transformPositionInverse(new Vector3d(w.x, w.y, w.z), new Vector3d());
        return new Vec3(l.x, l.y, l.z);
    }

    public static Vec3 worldDirOf(TrackNodeLocation location, Vec3 direction) {
        if (location == null || direction == null) {
            return null;
        }
        Pose3dc pose = poseOf(location, true);
        if (pose == null) {
            return direction;
        }
        Vector3d w = pose.transformNormal(new Vector3d(direction.x, direction.y, direction.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z).normalize();
    }

    public static void sweep() {
        if (sweepTicks++ % SWEEP_INTERVAL != 0) {
            return;
        }
        pruneStaleSeams();
        int passes = MAX_WELDS_PER_SWEEP;
        while (passes > 0 && weldOneSeam()) {
            passes--;
        }
    }

    private static boolean weldOneSeam() {
        GlobalRailwayManager manager = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = manager == null ? null : manager.trackNetworks;
        if (networks == null || networks.isEmpty()) {
            return false;
        }
        List<Railhead> heads = new ArrayList<>();
        for (TrackGraph graph : new ArrayList<>(networks.values())) {
            for (TrackNodeLocation location : new ArrayList<>(graph.getNodes())) {
                Railhead head = railheadOf(graph, graph.locateNode(location));
                if (head != null) {
                    heads.add(head);
                }
            }
        }
        for (int i = 0; i < heads.size(); i++) {
            Railhead x = heads.get(i);
            for (int j = i + 1; j < heads.size(); j++) {
                Railhead y = heads.get(j);
                if (x.world.distanceToSqr(y.world) > MAX_SEAM_GAP * MAX_SEAM_GAP
                        || x.alongRail.dot(y.alongRail) > FACING_DOT) {
                    continue;
                }
                if (!isSeam(x.node.getLocation(), y.node.getLocation())) {
                    continue;
                }
                if (seamOf(x.graph, x.node) != null || seamOf(y.graph, y.node) != null) {
                    continue;
                }
                if (onTurningPlate(x.node.getLocation()) || onTurningPlate(y.node.getLocation())) {
                    continue;
                }
                if (weld(x.graph, x.node, y.graph, y.node, x.material)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean onTurningPlate(TrackNodeLocation location) {
        Turntable plate = TurntableRegistry.byPlate(hostIdOf(location));
        return plate != null && !plate.turntableSettled();
    }

    public static int unweldSeamsAt(TrackNodeLocation head) {
        GlobalRailwayManager manager = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = manager == null ? null : manager.trackNetworks;
        if (networks == null || head == null) {
            return 0;
        }
        int dropped = 0;
        for (TrackGraph graph : new ArrayList<>(networks.values())) {
            TrackNode node = graph.locateNode(head);
            if (node == null) {
                continue;
            }
            int before = dropped;
            for (TrackNode other : new ArrayList<>(graph.getConnectionsFrom(node).keySet())) {
                TrackEdge edge = graph.getConnection(Couple.create(node, other));
                if (edge == null || !edge.isInterDimensional() || !isSeam(head, other.getLocation())) {
                    continue;
                }
                graph.disconnectNodes(node, other);
                dropped++;
            }
            if (dropped > before) {
                resync(graph);
            }
        }
        return dropped;
    }

    // travel spends nothing on a seam, so three railheads seamed to each other are a loop a scout never leaves
    private static TrackNode seamOf(TrackGraph graph, TrackNode node) {
        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
        if (connections == null) {
            return null;
        }
        for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
            if (entry.getValue().isInterDimensional() && isSeam(node.getLocation(), entry.getKey().getLocation())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void pruneStaleSeams() {
        GlobalRailwayManager manager = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = manager == null ? null : manager.trackNetworks;
        if (networks == null) {
            return;
        }
        for (TrackGraph graph : new ArrayList<>(networks.values())) {
            int dropped = 0;
            for (TrackNodeLocation location : new ArrayList<>(graph.getNodes())) {
                TrackNode node = graph.locateNode(location);
                Map<TrackNode, TrackEdge> connections = node == null ? null : graph.getConnectionsFrom(node);
                if (connections == null) {
                    continue;
                }
                TrackNode kept = null;
                for (TrackNode other : new ArrayList<>(connections.keySet())) {
                    TrackEdge edge = connections.get(other);
                    if (edge == null || !edge.isInterDimensional() || !isSeam(location, other.getLocation())) {
                        continue;
                    }
                    if (kept == null && aligned(location, other.getLocation())) {
                        kept = other;
                        continue;
                    }
                    graph.disconnectNodes(node, other);
                    dropped++;
                }
            }
            if (dropped > 0) {
                resync(graph);
            }
        }
    }

    // creates sync has no edge removal, so a client keeps a dropped seam and its own walks would loop on it
    private static void resync(TrackGraph graph) {
        graph.markDirty();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Create.RAILWAYS.sync.sendFullGraphTo(graph, player);
        }
    }

    public static Railhead railheadOf(TrackGraph graph, TrackNode node) {
        if (node == null) {
            return null;
        }
        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
        if (connections == null) {
            return null;
        }
        List<TrackEdge> rails = connections.values().stream()
                .filter(edge -> !edge.isInterDimensional())
                .limit(2)
                .toList();
        if (rails.size() != 1) {
            return null;
        }
        TrackEdge rail = rails.get(0);
        TrackNodeLocation at = node.getLocation();
        Vec3 world = worldPosOf(at);
        Vec3 along = worldDirOf(at,
                rail.node1 == node ? rail.getDirection(true) : rail.getDirection(false).scale(-1.0));
        if (world == null || along == null) {
            return null;
        }
        return new Railhead(graph, node, world, along, rail.getTrackMaterial());
    }

    public record Railhead(TrackGraph graph, TrackNode node, Vec3 world, Vec3 alongRail, TrackMaterial material) {
    }

    // a turntables far side has to be visible to the planner before any edge joins it, so the merge stands alone
    public static TrackGraph mergeGraphs(TrackGraph graphA, TrackGraph graphB) {
        if (graphA == graphB || graphA == null || graphB == null) {
            return graphA == null ? graphB : graphA;
        }
        TrackGraph from = graphA.getNodes().size() < graphB.getNodes().size() ? graphA : graphB;
        TrackGraph into = from == graphA ? graphB : graphA;
        NeoForge.EVENT_BUS.post(new TrackGraphMergeEvent(from, into));
        from.transferAll(into);
        Create.RAILWAYS.removeGraphAndGroup(from);
        Create.RAILWAYS.sync.graphRemoved(from);
        return into;
    }

    // a merge can retire the graph a carriage still holds, the node a point stands on knows the live one
    public static TrackGraph liveGraphOf(TravellingPoint point, TrackGraph held) {
        TrackGraph live = point == null || point.node1 == null
                ? null : TurntableRegistry.graphHolding(point.node1.getLocation());
        return live != null ? live : held;
    }

    public static boolean weld(TrackGraph graphA, TrackNode a, TrackGraph graphB, TrackNode b, TrackMaterial material) {
        if (graphA == null || graphB == null || a == null || b == null) {
            return false;
        }
        TrackNodeLocation la = a.getLocation();
        TrackNodeLocation lb = b.getLocation();
        if (!isSeam(la, lb)) {
            return false;
        }
        Vec3 pa = worldPosOf(la);
        Vec3 pb = worldPosOf(lb);
        double gap = pa.distanceTo(pb);
        if (gap > MAX_SEAM_GAP) {
            return false;
        }

        GlobalRailwayManager manager = Create.RAILWAYS;
        TrackGraph graph = mergeGraphs(graphA, graphB);

        TrackNode n1 = graph.locateNode(la);
        TrackNode n2 = graph.locateNode(lb);
        if (n1 == null || n2 == null) {
            return false;
        }
        if (graph.getConnection(Couple.create(n1, n2)) != null) {
            return true;
        }
        if (seamOf(graph, n1) != null || seamOf(graph, n2) != null) {
            return false;
        }

        TrackMaterial mat = material == null ? TrackMaterial.ANDESITE : material;
        TrackEdge forward = new TrackEdge(n1, n2, null, mat);
        TrackEdge backward = new TrackEdge(n2, n1, null, mat);
        graph.putConnection(n1, n2, forward);
        graph.putConnection(n2, n1, backward);
        manager.sync.edgeAdded(graph, n1, n2, forward);
        manager.sync.edgeAdded(graph, n2, n1, backward);
        graph.markDirty();

        return true;
    }

    public static boolean weldNow(TrackNodeLocation a, TrackNodeLocation b) {
        GlobalRailwayManager manager = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = manager == null ? null : manager.trackNetworks;
        if (networks == null || a == null || b == null) {
            return false;
        }
        TrackGraph graphA = null;
        TrackGraph graphB = null;
        for (TrackGraph graph : new ArrayList<>(networks.values())) {
            if (graphA == null && graph.locateNode(a) != null) {
                graphA = graph;
            }
            if (graphB == null && graph.locateNode(b) != null) {
                graphB = graph;
            }
        }
        if (graphA == null || graphB == null) {
            return false;
        }
        Railhead head = railheadOf(graphA, graphA.locateNode(a));
        return weld(graphA, graphA.locateNode(a), graphB, graphB.locateNode(b),
                head == null ? null : head.material());
    }

    public static SubLevel hostAt(Level level, BlockPos pos) {
        SubLevelContainer container = level == null || pos == null ? null : SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos.getX() >> 4, pos.getZ() >> 4)) {
            return null;
        }
        LevelPlot plot = container.getPlot(new ChunkPos(pos));
        return plot == null ? null : plot.getSubLevel();
    }

    private static SubLevel hostIn(Level level, TrackNodeLocation location) {
        if (location == null || location.dimension == null) {
            return null;
        }
        SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null || !inPlotGrid(container, location)) {
            return null;
        }
        Vec3 v = location.getLocation();
        LevelPlot plot = container.getPlot(new ChunkPos(BlockPos.containing(v.x, v.y, v.z)));
        return plot == null ? null : plot.getSubLevel();
    }

    private static boolean inPlotGrid(SubLevelContainer container, TrackNodeLocation location) {
        return container.inBounds(Mth.floor(location.getX() / 2.0) >> 4, Mth.floor(location.getZ() / 2.0) >> 4);
    }

    private static SubLevelContainer anyContainer(ResourceKey<Level> dimension) {
        Level level = serverLevel(dimension);
        if (level == null) {
            level = CLIENT_LEVELS.get(dimension);
        }
        return level == null ? null : SubLevelContainer.getContainer(level);
    }

    private static Level serverLevel(ResourceKey<Level> dimension) {
        MinecraftServer server = dimension == null ? null : ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(dimension);
    }
}
