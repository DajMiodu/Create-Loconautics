package com.lycoris.loconautics.trainphysics.visual;

import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import org.joml.Vector3d;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class BogeyRailPoints {

    // create only pathfinds 32 blocks for a correction, past that the point is somewhere else entirely
    private static final float MAX_CORRECTION = 32.0f;

    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private static final CarriageSyncData GRAPH_WALKER = new CarriageSyncData();

    private static final double MAX_SLIDE = 2.0;

    private static final double PACKET_SKEW_TICKS = 1.0;

    private static final class State {
        final TravellingPoint leading = new TravellingPoint();
        final TravellingPoint trailing = new TravellingPoint();
        UUID graphId;
        boolean seeded;
        // the span runs against the car, so the walked chord has to be turned round before it becomes a render frame
        boolean chordFlipped;
    }

    private static final Map<AbstractBogeyBlockEntity, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BogeyRailPoints() {
    }

    public static void forget(AbstractBogeyBlockEntity be) {
        STATES.remove(be);
    }

    public static void tick(AbstractBogeyBlockEntity be) {
        BogeyYawVisual.Link link = BogeyYawVisual.getRailLink(be);
        if (link == null || be.getLevel() == null) {
            forget(be);
            return;
        }
        TrackGraph graph = graphOf(be, link.graphId());
        if (graph == null) {
            forget(be);
            return;
        }
        TravellingPoint lead = pointOf(graph, link.leadNode1(), link.leadNode2(), link.leadPos());
        TravellingPoint trail = pointOf(graph, link.trailNode1(), link.trailNode2(), link.trailPos());
        if (lead == null || trail == null) {
            forget(be);
            return;
        }

        State state = STATES.computeIfAbsent(be, k -> new State());
        synchronized (state) {
            state.chordFlipped = link.chordFlipped();
            if (!state.seeded || !link.graphId().equals(state.graphId)) {
                copy(lead, state.leading);
                copy(trail, state.trailing);
                state.graphId = link.graphId();
                state.seeded = true;
                return;
            }
            approach(graph, state.leading, lead);
            approach(graph, state.trailing, trail);
        }
    }

    public static ClientSubLevel railHost(AbstractBogeyBlockEntity be) {
        State state = STATES.get(be);
        if (state == null) {
            return null;
        }
        synchronized (state) {
            TrackEdge edge = state.seeded ? state.leading.edge : null;
            if (edge == null || edge.node1 == null) {
                return null;
            }
            return RailSeams.clientHostOf(edge.node1.getLocation()) instanceof ClientSubLevel client ? client : null;
        }
    }

    public record Chord(Vec3 forward, Vec3 up) { }

    // the whole point of the port an angle read off the rail where the car is drawn, never a slerp between angles
    public static Chord chordAt(AbstractBogeyBlockEntity be, ClientSubLevel body) {
        State state = STATES.get(be);
        if (state == null) {
            return null;
        }
        synchronized (state) {
            if (!state.seeded) {
                return null;
            }
            TrackGraph graph = graphOf(be, state.graphId);
            if (graph == null) {
                return null;
            }
            TravellingPoint lead = new TravellingPoint();
            TravellingPoint trail = new TravellingPoint();
            copy(state.leading, lead);
            copy(state.trailing, trail);
            slideUnderBody(graph, body, be, lead, trail);
            Vec3 a = worldPos(lead, graph);
            Vec3 b = worldPos(trail, graph);
            if (a == null || b == null || !nearBody(body, be, a, b)) {
                return null;
            }
            Vec3 chord = a.subtract(b);
            if (chord.lengthSqr() < 1.0e-9) {
                return null;
            }
            chord = chord.normalize();
            return new Chord(state.chordFlipped ? chord.reverse() : chord, railUp(lead));
        }
    }

    private static void slideUnderBody(TrackGraph graph, ClientSubLevel body, AbstractBogeyBlockEntity be,
                                       TravellingPoint lead, TravellingPoint trail) {
        if (body == null) {
            return;
        }
        Vec3 a = worldPos(lead, graph);
        Vec3 b = worldPos(trail, graph);
        if (a == null || b == null || a.distanceToSqr(b) < 1.0e-9) {
            return;
        }
        Vec3 f = a.subtract(b).normalize();
        Vector3d drawn = drawnCenter(body, be);
        Vec3 center = a.add(b).scale(0.5);
        double slip = (drawn.x - center.x) * f.x + (drawn.y - center.y) * f.y + (drawn.z - center.z) * f.z;
        if (Math.abs(slip) < 1.0e-6 || Math.abs(slip) > maxSlide(body)) {
            return;
        }
        double signed = slip * edgeSign(lead, f);
        TravellingPoint front = slip > 0.0 ? lead : trail;
        TravellingPoint rear = front == lead ? trail : lead;
        double moved = front.travel(graph, signed, front.steer(TravellingPoint.SteerDirection.NONE, UP),
                front.ignoreEdgePoints(), front.ignoreTurns(), front.ignorePortals());
        rear.travel(graph, moved, rear.follow(front),
                rear.ignoreEdgePoints(), rear.ignoreTurns(), rear.ignorePortals());
    }

    private static Vector3d drawnCenter(ClientSubLevel body, AbstractBogeyBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return body.renderPose().transformPosition(
                new Vector3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), new Vector3d());
    }

    // a point past the portal is another scenes raw coordinates, the chord to it points at the moon
    private static boolean nearBody(ClientSubLevel body, AbstractBogeyBlockEntity be, Vec3 a, Vec3 b) {
        if (body == null) {
            return true;
        }
        Vector3d drawn = drawnCenter(body, be);
        Vec3 center = a.add(b).scale(0.5);
        double reach = maxSlide(body);
        return drawn.distanceSquared(center.x, center.y, center.z) <= reach * reach;
    }

    private static double maxSlide(ClientSubLevel body) {
        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(body.getUniqueId());
        double speed = marker == null ? 0.0 : Math.abs(marker.speed());
        double lagTicks = 2.0 * SableClientConfig.INTERPOLATION_DELAY.getAsDouble() + PACKET_SKEW_TICKS;
        return MAX_SLIDE + speed * lagTicks;
    }

    private static double edgeSign(TravellingPoint point, Vec3 spanForward) {
        Vec3 d = point.edge.getDirectionAt(point.position);
        if (d == null || d.lengthSqr() < 1.0e-9) {
            return 1.0;
        }
        SubLevel host = RailSeams.clientHostOf(point.edge.node1.getLocation());
        if (host instanceof ClientSubLevel client) {
            Vector3d w = client.renderPose().transformNormal(new Vector3d(d.x, d.y, d.z), new Vector3d());
            d = new Vec3(w.x, w.y, w.z);
        }
        return d.dot(spanForward) >= 0.0 ? 1.0 : -1.0;
    }

    private static Vec3 railUp(TravellingPoint point) {
        Vec3 up = point != null && point.upsideDown ? new Vec3(0.0, -1.0, 0.0) : UP;
        if (point == null || point.edge == null || point.edge.node1 == null) {
            return up;
        }
        SubLevel host = RailSeams.clientHostOf(point.edge.node1.getLocation());
        return host instanceof ClientSubLevel client ? client.renderPose().transformNormal(up) : up;
    }

    private static Vec3 worldPos(TravellingPoint point, TrackGraph graph) {
        if (point == null || point.edge == null || point.edge.node1 == null) {
            return null;
        }
        Vec3 local = point.getPosition(graph);
        if (local == null) {
            return null;
        }
        SubLevel host = RailSeams.clientHostOf(point.edge.node1.getLocation());
        if (!(host instanceof ClientSubLevel client)) {
            return local;
        }
        Vector3d w = client.renderPose().transformPosition(new Vector3d(local.x, local.y, local.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    public static org.joml.Quaternionf worldFrame(Chord chord, org.joml.Quaternionf dest) {
        Vec3 forward = chord.forward();
        org.joml.Vector3f f = new org.joml.Vector3f((float) forward.x, (float) forward.y, (float) forward.z);
        f.normalize();
        org.joml.Vector3f up = new org.joml.Vector3f((float) chord.up().x, (float) chord.up().y,
                (float) chord.up().z);
        up.sub(new org.joml.Vector3f(f).mul(up.dot(f)));
        if (up.lengthSquared() < 1.0e-6f) {
            up.set(1.0f, 0.0f, 0.0f).sub(new org.joml.Vector3f(f).mul(f.x));
        }
        up.normalize();
        org.joml.Vector3f right = new org.joml.Vector3f(up).cross(f);
        org.joml.Matrix3f basis = new org.joml.Matrix3f();
        basis.setColumn(0, right);
        basis.setColumn(1, up);
        basis.setColumn(2, f);
        return basis.getNormalizedRotation(dest);
    }

    private static void approach(TrackGraph graph, TravellingPoint own, TravellingPoint target) {
        float found = -1.0f;
        boolean forward = false;
        for (boolean f : new boolean[] { true, false }) {
            float d = GRAPH_WALKER.getDistanceTo(graph, own, target, MAX_CORRECTION, f);
            if (d > 0 && (found == -1.0f || d < found)) {
                found = d;
                forward = f;
            }
        }
        if (found == -1.0f) {
            copy(target, own);
            return;
        }
        boolean[] onPath = { true };
        own.travel(graph, (forward ? 1.0 : -1.0) * found,
                own.follow(target, hit -> onPath[0] &= hit),
                own.ignoreEdgePoints(), own.ignoreTurns(), own.ignorePortals());
        if (!onPath[0]) {
            copy(target, own);
        }
    }

    private static TravellingPoint pointOf(TrackGraph graph, int netId1, int netId2, float position) {
        TrackNode node1 = graph.getNode(netId1);
        TrackNode node2 = graph.getNode(netId2);
        if (node1 == null || node2 == null) {
            return null;
        }
        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node1);
        TrackEdge edge = connections.get(node2);
        if (edge == null) {
            return null;
        }
        return new TravellingPoint(node1, node2, edge, position, false);
    }

    private static void copy(TravellingPoint from, TravellingPoint to) {
        to.node1 = from.node1;
        to.node2 = from.node2;
        to.edge = from.edge;
        to.position = from.position;
        to.upsideDown = from.upsideDown;
    }

    private static TrackGraph graphOf(AbstractBogeyBlockEntity be, UUID graphId) {
        if (graphId == null || be.getLevel() == null) {
            return null;
        }
        return CreateClient.RAILWAYS.sided(be.getLevel()).trackNetworks.get(graphId);
    }
}
