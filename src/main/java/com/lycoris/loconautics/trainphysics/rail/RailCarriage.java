package com.lycoris.loconautics.trainphysics.rail;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.compat.railways.RailwaysSwitches;
import com.lycoris.loconautics.foundation.debug.SteerTrace;
import com.lycoris.loconautics.trainphysics.portal.PortalFrame;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.IPortalListener;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import com.simibubi.create.content.trains.track.TrackMaterial;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.lycoris.loconautics.foundation.math.VecMath.perpendicularUp;

public final class RailCarriage {

    private TrackGraph graph;
    private final TravellingPoint leading;
    private final TravellingPoint trailing;
    private boolean reachedEnd;
    // a portal a point stopped short of because it could not be crossed, a buffer, not a railhead
    private Couple<TrackNodeLocation> pendingPortal;
    private PortalFrame portal;
    private Vec3 steerTarget;
    private TurnLog turnLog;
    private boolean turnLeader;
    private long turnCursor;
    private TurnMemory turnMemory;
    private TurnMemory.BogeyId turnId;
    private TravellingPoint followTarget;
    private String junctionTag = "unset";
    private List<net.createmod.catnip.data.Couple<TrackNode>> path;
    private final double bogeySpacing;
    private final Vec3 upNormal;
    private double speed;
    // which way the car means to go, kept apart from speed because the follow correction can invert speed for a tick
    private double travelIntent;
    private boolean stopped;
    private double lastTravelSign = 1.0;
    private boolean independent;

    private Vector3d forward0;
    private Vector3d up0;

    private RailCarriage(TrackGraph graph, TravellingPoint leading, TravellingPoint trailing, Vec3 upNormal,
                         double bogeySpacing, double speed) {
        this.graph = graph;
        this.leading = leading;
        this.trailing = trailing;
        this.bogeySpacing = bogeySpacing;
        this.upNormal = upNormal;
        this.speed = speed;
        captureReference();
    }

    public static RailCarriage at(TrackGraphLocation location, Vec3 upNormal, double bogeySpacing, double speed) {
        TravellingPoint seed = RailFollower.pointAt(location);
        return seed == null ? null : seededAt(seed, location.graph, upNormal, bogeySpacing, speed);
    }

    // a bezier curve has no track blocks between its ends, so a split island has to inherit a live point instead of scanning the world for one
    public static RailCarriage seededAt(TravellingPoint seed, TrackGraph graph, Vec3 upNormal,
                                        double bogeySpacing, double speed) {
        if (seed == null || seed.edge == null || graph == null) {
            return null;
        }
        RailCarriage carriage = new RailCarriage(graph, copyOf(seed), copyOf(seed), upNormal, bogeySpacing, speed);
        carriage.seatSpan(seed, graph, bogeySpacing / 2.0);
        carriage.captureReference();
        return carriage;
    }

    private static TravellingPoint copyOf(TravellingPoint src) {
        return new TravellingPoint(src.node1, src.node2, src.edge, src.position, src.upsideDown);
    }

    public TravellingPoint seedPoint() {
        if (trailing == null || trailing.edge == null) {
            return null;
        }
        TravellingPoint seed = copyOf(trailing);
        seed.travel(graph, bogeySpacing / 2.0, seed.steer(SteerDirection.NONE, upNormal),
                seed.ignoreEdgePoints(), seed.ignoreTurns(), seed.ignorePortals());
        return seed.edge == null ? null : seed;
    }

    private double clampStress(double stress) {
        double limit = bogeySpacing * 0.5;
        return stress < -limit ? -limit : (stress > limit ? limit : stress);
    }

    public Vec3 tick() {
        adoptLiveGraph();
        // physics substeps run between server ticks, so a rebuilt graph must not be walked with stale nodes
        rebind();
        Vec3 hopCenterBefore = hopCenter();
        String hopKindBefore = hopKind(leading) + "/" + hopKind(trailing);
        double hopMoved = 0.0;
        if (!stopped && speed != 0.0) {
            Couple<TrackNodeLocation> heldPortal = pendingPortal;
            pendingPortal = null;
            boolean towardHigherPosition = travelIntent != 0.0 ? travelIntent > 0.0 : speed > 0.0;
            TravellingPoint front = towardHigherPosition == leadingAtHigherPosition() ? leading : trailing;
            TravellingPoint rear = front == leading ? trailing : leading;
            ITrackSelector chosenSelector = turnLog != null && !turnLeader
                    ? replaySelector(front)
                    : (path != null && !path.isEmpty())
                            ? pathSelector()
                            : followTarget != null && turnLog == null
                                    ? front.follow(followTarget)
                                    : steerSelector(front);
            String selectorKind = turnLog != null && !turnLeader ? "replay"
                    : (path != null && !path.isEmpty()) ? "path"
                    : followTarget != null && turnLog == null ? "follow" : "steer";
            ITrackSelector frontSelector = (g, pair) -> {
                java.util.Map.Entry<TrackNode, TrackEdge> entry = chosenSelector.apply(g, pair);
                LoconauticsConstants.LOGGER.info("[issue274] junction {} selector={} options={} picked={} of {} speed={} "
                        + "intent={} from={}", junctionTag, selectorKind, pair.getSecond().size(),
                        pair.getSecond().indexOf(entry), nodeList(pair.getSecond()), String.format("%.3f", speed),
                        String.format("%.3f", travelIntent), front.edge == null ? "none"
                                : front.edge.node1.getLocation().getLocation() + ">"
                                        + front.edge.node2.getLocation().getLocation());
                return settleTurn(g, front, pair.getFirst(), pair.getSecond(), entry);
            };
            // creates carriage.travel stress term. without it a spacing error never heals and the chord wanders.
            double stress = clampStress(bogeySpacing - leadingPos().distanceTo(trailingPos()));
            double frontCorrection = (towardHigherPosition ? 0.5 : -0.5) * stress;
            double rearCorrection = -frontCorrection;
            double moved;
            RailwaysSwitches.beginTravel(front, rear);
            try {
                moved = front.travel(graph, speed + frontCorrection, frontSelector,
                        front.ignoreEdgePoints(), front.ignoreTurns(), portalGate(front));
                // the rear follows what the front actually moved, a clamped or blocked front would otherwise desync the pair
                rear.travel(graph, moved + rearCorrection, rear.follow(front),
                        rear.ignoreEdgePoints(), rear.ignoreTurns(), portalGate(rear));
            } finally {
                RailwaysSwitches.endTravel();
            }
            hopMoved = moved;
            if (pendingPortal == null && Math.abs(moved) <= END_LATCH_TRAVEL) {
                pendingPortal = heldPortal;
            }
            if (pendingPortal != null) {
                reachedEnd = false;
            } else if (front.blocked) {
                if (!reachedEnd) {
                }
                reachedEnd = true;
                stopped = true;
            } else if (Math.abs(moved) > END_LATCH_TRAVEL) {
                // between two game ticks, so the derail check never saw the end it had already reached
                reachedEnd = false;
            }
        }
        traceHopSnap(hopCenterBefore, hopKindBefore, hopMoved);
        return leadingPos().add(trailingPos()).scale(0.5);
    }

    private Vec3 hopCenter() {
        Vec3 a = worldPos(leading);
        Vec3 b = worldPos(trailing);
        return a == null || b == null ? null : a.add(b).scale(0.5);
    }

    private String hopKind(TravellingPoint p) {
        if (p == null || p.edge == null || p.edge.node1 == null) {
            return "none";
        }
        TrackNodeLocation at = p.edge.node1.getLocation();
        if (p.edge.isInterDimensional()) {
            return RailSeams.isSeam(at, p.edge.node2.getLocation()) ? "SEAM" : "PORTAL";
        }
        if (!RailSeams.inPlotGrid(at)) {
            return "WORLD";
        }
        return RailSeams.serverHostOf(at) != null ? "PLOT-live" : (poseOfEdge(p.edge) != null ? "PLOT-frame" : "PLOT-NOPOSE");
    }

    private String hopPoint(TravellingPoint p) {
        if (p == null || p.edge == null) {
            return "none";
        }
        Vec3 w = worldPos(p);
        Vec3 d = p.edge.getDirectionAt(p.position);
        Vec3 wd = d == null ? null : homeDir(p.edge, d);
        return String.format("%s pos=%.3f/%.4f world=%s dir=%s blocked=%s", hopKind(p), p.position, p.edge.getLength(),
                hopVec(w), hopVec(wd), p.blocked);
    }

    // temporary, tag cartmove
    public String moveLine() {
        Vec3 center = hopCenter();
        return String.format("center=%s span=%.3f lead[%s] trail[%s]", hopVec(center),
                leadingPos().distanceTo(trailingPos()), hopPoint(leading), hopPoint(trailing));
    }

    public Vec3 moveCenter() {
        return hopCenter();
    }

    private static String hopVec(Vec3 v) {
        return v == null ? "null" : String.format("(%.3f,%.3f,%.3f)", v.x, v.y, v.z);
    }

    // temporary, tag hopsnap
    private void traceHopSnap(Vec3 centerBefore, String kindBefore, double moved) {
        Vec3 centerAfter = hopCenter();
        String kindAfter = hopKind(leading) + "/" + hopKind(trailing);
        double jump = centerBefore == null || centerAfter == null ? 0.0 : centerAfter.distanceTo(centerBefore);
        double excess = Math.abs(jump - Math.abs(moved));
        boolean onSeam = kindAfter.contains("SEAM") || kindBefore.contains("SEAM");
        if (!onSeam && excess < 0.2 && kindBefore.equals(kindAfter)) {
            return;
        }
        LoconauticsConstants.LOGGER.info("[hopsnap] rail={} kind {} -> {} moved={} centerJump={} excess={} gapSpan={} "
                        + "lead[{}] trail[{}] centerBefore={} centerAfter={}", System.identityHashCode(this) & 0xffff,
                kindBefore, kindAfter, String.format("%.4f", moved), String.format("%.4f", jump),
                String.format("%.4f", excess), String.format("%.4f", leadingPos().distanceTo(trailingPos())),
                hopPoint(leading), hopPoint(trailing), hopVec(centerBefore), hopVec(centerAfter));
    }

    private boolean leadingAtHigherPosition() {
        Vec3 tangent = edgeTangent();
        Vec3 lead = leadingPos();
        Vec3 trail = trailingPos();
        if (tangent == null || lead == null || trail == null) {
            return true;
        }
        Vec3 span = lead.subtract(trail);
        return span.lengthSqr() < 1.0e-9 || tangent.dot(span) >= 0.0;
    }

    public boolean reachedEnd() {
        return reachedEnd;
    }

    public Vec3 railheadOutward() {
        TravellingPoint p = leading.blocked && leading.edge != null ? leading
                : (trailing.blocked && trailing.edge != null ? trailing : null);
        if (p == null || p.edge.isInterDimensional()) {
            return null;
        }
        Vec3 d = p.edge.getDirectionAt(p.position);
        if (d == null || d.lengthSqr() < 1.0e-9) {
            return null;
        }
        Vec3 w = homeDir(p.edge, d);
        if (w == null || w.lengthSqr() < 1.0e-9) {
            return null;
        }
        w = w.normalize();
        return p.position * 2.0 >= p.edge.getLength() ? w : w.scale(-1.0);
    }

    private static final double END_LATCH_TRAVEL = 1.0e-3;

    public boolean railheadWithin(double distance) {
        return blockedWithin(distance) || blockedWithin(-distance);
    }

    private boolean blockedWithin(double distance) {
        TravellingPoint probe = seedPoint();
        if (probe == null) {
            return false;
        }
        probe.travel(graph, distance, probe.steer(SteerDirection.NONE, upNormal),
                probe.ignoreEdgePoints(), probe.ignoreTurns(), probe.ignorePortals());
        return probe.blocked;
    }

    public Couple<TrackNodeLocation> pendingPortal() {
        return pendingPortal;
    }

    public double travelIntent() {
        return travelIntent;
    }

    public PortalFrame portal() {
        return portal;
    }

    public void setPortal(PortalFrame frame) {
        this.portal = frame;
    }

    public void clearPortal() {
        pendingPortal = null;
        portal = null;
    }

    private IPortalListener portalGate(TravellingPoint point) {
        return crossing -> {
            if (RailSeams.crossable(crossing)) {
                return false;
            }
            if (portal == null) {
                portal = PortalFrame.of(graph, crossing, dimOf(point.edge));
            }
            if (portal != null && portal.isPortal(crossing)) {
                return false;
            }
            pendingPortal = crossing;
            return true;
        };
    }

    public boolean anyPointFar() {
        return isFar(leading) || isFar(trailing);
    }

    // the body overhangs its bogeys, so waiting for a rail point to reach a portal leaves the overhang uncut
    public PortalFrame portalWithin(double distance) {
        TravellingPoint seed = seedPoint();
        if (seed == null) {
            return null;
        }
        ResourceKey<Level> home = dimOf(seed.edge);
        PortalFrame ahead = portalAlong(seed, distance, home);
        return ahead != null ? ahead : portalAlong(seedPoint(), -distance, home);
    }

    private PortalFrame portalAlong(TravellingPoint probe, double distance, ResourceKey<Level> home) {
        AtomicReference<Couple<TrackNodeLocation>> hit = new AtomicReference<>();
        probe.travel(graph, distance, probe.steer(SteerDirection.NONE, upNormal), probe.ignoreEdgePoints(),
                probe.ignoreTurns(), crossing -> {
                    if (RailSeams.crossable(crossing)) {
                        return false;
                    }
                    hit.set(crossing);
                    return true;
                });
        return hit.get() == null ? null : PortalFrame.of(graph, hit.get(), home);
    }

    public boolean isFar(TravellingPoint p) {
        return portal != null && p != null && p.edge != null && portal.farDim().equals(dimOf(p.edge));
    }

    private boolean isFarEdge(TrackEdge edge) {
        return portal != null && edge != null && portal.farDim().equals(dimOf(edge));
    }

    private static ResourceKey<Level> dimOf(TrackEdge edge) {
        return edge == null || edge.node1 == null ? null : edge.node1.getLocation().dimension;
    }

    private Vec3 toHome(TrackEdge edge, Vec3 v) {
        return v == null || !isFarEdge(edge) ? v : portal.toNear(v);
    }

    private Vec3 homeDir(TrackEdge edge, Vec3 d) {
        Vec3 w = worldDir(poseOfEdge(edge), d);
        return w == null || !isFarEdge(edge) ? w : portal.dirToNear(w);
    }

    private static final double DETECT_RANGE = 3.0;

    public SableRailHop.Result railAheadTarget(ServerLevel level, ServerSubLevel selfBody, Vec3 worldPos,
                                               Vec3 tangent, Vec3 worldVert, boolean sweepAhead) {
        SableRailHop.Probe probe = railAheadProbe(level, selfBody, worldPos, tangent, worldVert, sweepAhead);
        return probe == null ? null : probe.hit();
    }

    public SableRailHop.Probe railAheadProbe(ServerLevel level, ServerSubLevel selfBody, Vec3 worldPos,
                                             Vec3 tangent, Vec3 worldVert, boolean sweepAhead) {
        return worldPos == null || isFar(leading) ? null
                : SableRailHop.probe(level, graph, hostOf(leading), selfBody, worldPos, tangent, worldVert,
                        DETECT_RANGE, sweepAhead);
    }

    public boolean weldAheadTo(SableRailHop.Result target) {
        if (target == null || target.location() == null || target.graph() == null || target.railPoint() == null) {
            return false;
        }
        adoptLiveGraph();
        TravellingPoint at = blockedNearest(target.railPoint());
        if (at == null || isFar(at)) {
            return false;
        }
        Vec3 here = worldPos(at);
        if (here == null) {
            return false;
        }
        TrackNode theirs = nearer(target.graph().locateNode(target.location().edge.getFirst()),
                target.graph().locateNode(target.location().edge.getSecond()), here);
        if (theirs == null) {
            return false;
        }
        TrackNode ours = nearer(at.node1, at.node2, RailSeams.worldPosOf(theirs.getLocation()));
        return RailSeams.weld(graph, ours, target.graph(), theirs, at.edge.getTrackMaterial());
    }

    private TravellingPoint blockedNearest(Vec3 to) {
        TravellingPoint a = leading.blocked && leading.edge != null ? leading : null;
        TravellingPoint b = trailing.blocked && trailing.edge != null ? trailing : null;
        if (a == null || b == null) {
            return a != null ? a : b;
        }
        return worldPos(a).distanceToSqr(to) <= worldPos(b).distanceToSqr(to) ? a : b;
    }

    private static TrackNode nearer(TrackNode a, TrackNode b, Vec3 to) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        Vec3 pa = RailSeams.worldPosOf(a.getLocation());
        Vec3 pb = RailSeams.worldPosOf(b.getLocation());
        if (pa == null) {
            return b;
        }
        return pb == null || pa.distanceToSqr(to) <= pb.distanceToSqr(to) ? a : b;
    }

    private boolean seatSpan(TravellingPoint seed, TrackGraph newGraph, double half) {
        this.graph = newGraph;
        copyPoint(leading, seed);
        leading.travel(graph, half, leading.steer(SteerDirection.NONE, upNormal),
                leading.ignoreEdgePoints(), leading.ignoreTurns(), leading.ignorePortals());
        copyPoint(trailing, seed);
        trailing.travel(graph, -half, trailing.steer(SteerDirection.NONE, upNormal),
                trailing.ignoreEdgePoints(), trailing.ignoreTurns(), trailing.ignorePortals());
        return worldPos(leading) != null && worldPos(trailing) != null;
    }

    // a welded follower straddles the seam, so the frame a point lives in is the frame of its own edge, never one
    private static ServerSubLevel hostOf(TravellingPoint p) {
        return p == null ? null : hostOfEdge(p.edge);
    }

    private static ServerSubLevel hostOfEdge(TrackEdge edge) {
        return edge == null || edge.node1 == null ? null : RailSeams.serverHostOf(edge.node1.getLocation());
    }

    // falls back to the saved frame when the host body is parked
    private static Pose3dc poseOfEdge(TrackEdge edge) {
        return edge == null || edge.node1 == null ? null : RailSeams.serverPoseOf(edge.node1.getLocation());
    }

    private static Pose3dc poseOf(TravellingPoint p) {
        return p == null ? null : poseOfEdge(p.edge);
    }

    public boolean railHostMissing() {
        return hostMissing(leading) || hostMissing(trailing);
    }

    private static boolean hostMissing(TravellingPoint p) {
        return p != null && p.edge != null && p.edge.node1 != null
                && RailSeams.hostBodyMissing(p.edge.node1.getLocation());
    }

    public Vec3 worldPointOf(TravellingPoint p) {
        return worldPos(p);
    }

    private Vec3 worldPos(TravellingPoint p) {
        if (p == null || p.edge == null) {
            return null;
        }
        Vec3 v = p.edge.getLength() < 1.0e-9 ? nodePoint(p) : p.getPosition(graph);
        Pose3dc pose = poseOf(p);
        if (v == null) {
            return null;
        }
        if (pose != null) {
            Vector3d w = pose.transformPosition(new Vector3d(v.x, v.y, v.z), new Vector3d());
            v = new Vec3(w.x, w.y, w.z);
        }
        return toHome(p.edge, v);
    }

    private static Vec3 nodePoint(TravellingPoint p) {
        Vec3 normal = p.node1.getNormal();
        return p.node1.getLocation().getLocation().add(p.upsideDown ? normal.scale(-1.0) : normal);
    }

    private static Vec3 worldDir(Pose3dc pose, Vec3 d) {
        if (d == null || pose == null) {
            return d;
        }
        Vector3d w = pose.transformNormal(new Vector3d(d.x, d.y, d.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    private static String nodeList(java.util.List<java.util.Map.Entry<TrackNode, TrackEdge>> options) {
        StringBuilder out = new StringBuilder();
        for (java.util.Map.Entry<TrackNode, TrackEdge> option : options) {
            out.append(option.getKey().getLocation().getLocation()).append(' ');
        }
        return out.toString().trim();
    }

    public void setJunctionTag(String tag) {
        this.junctionTag = tag;
    }

    private ITrackSelector steerSelector(TravellingPoint point) {
        return (g, pair) -> {
            java.util.List<java.util.Map.Entry<TrackNode, TrackEdge>> targets = pair.getSecond();
            if (targets.size() == 1) {
                return recordTurn(targets.get(0));
            }
            boolean forward = pair.getFirst();
            Vec3 trajectory = point.edge.isInterDimensional()
                    ? forward()
                    : homeDir(point.edge, forward ? point.edge.getDirection(false)
                            : point.edge.getDirection(true).scale(-1.0));
            if (steerTarget == null && followTarget != null) {
                java.util.Map.Entry<TrackNode, TrackEdge> followed = point.follow(followTarget).apply(g, pair);
                SteerTrace.pick(this, "follow target edge " + followTarget.edge.node1.getLocation().getLocation()
                        + " pos " + followTarget.position, targets.size(), followed == targets.get(0) ? "first" : "other");
                return recordTurn(followed);
            }
            boolean unprompted = steerTarget == null;
            java.util.Map.Entry<TrackNode, TrackEdge> best = bestByGoal(targets, unprompted ? trajectory : steerTarget,
                    unprompted);
            java.util.Map.Entry<TrackNode, TrackEdge> picked = best != null ? best : targets.get(0);
            SteerTrace.pick(this, unprompted ? "straight, no intent and no follow" : "intent", targets.size(),
                    picked == targets.get(0) ? "first" : "other");
            return recordTurn(picked);
        };
    }

    private java.util.Map.Entry<TrackNode, TrackEdge> bestByGoal(
            java.util.List<java.util.Map.Entry<TrackNode, TrackEdge>> targets, Vec3 goal, boolean straight) {
        java.util.Map.Entry<TrackNode, TrackEdge> best = null;
        double bestScore = -Double.MAX_VALUE;
        for (java.util.Map.Entry<TrackNode, TrackEdge> entry : targets) {
            Vec3 outgoing = homeDir(entry.getValue(), entry.getValue().getDirection(true));
            Vec3 far = homeDir(entry.getValue(), entry.getValue().getDirection(false));
            double near = goal.x * outgoing.x + goal.z * outgoing.z;
            double away = goal.x * far.x + goal.z * far.z;
            double score = straight ? near + away : near;
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return best;
    }

    public void setTurnMemory(TurnMemory memory, TurnMemory.BogeyId id) {
        this.turnMemory = memory;
        this.turnId = id;
    }

    private java.util.Map.Entry<TrackNode, TrackEdge> settleTurn(TrackGraph g, TravellingPoint front, boolean forward,
            java.util.List<java.util.Map.Entry<TrackNode, TrackEdge>> options,
            java.util.Map.Entry<TrackNode, TrackEdge> own) {
        if (turnMemory == null || front.edge == null) {
            return own;
        }
        if (options.size() < 2) {
            turnMemory.unwind(front.edge, forward, turnId);
            return own;
        }
        return turnMemory.settle(front.edge, forward, options, own, plannedTurn(g, options), turnId);
    }

    private java.util.Map.Entry<TrackNode, TrackEdge> plannedTurn(TrackGraph g,
            java.util.List<java.util.Map.Entry<TrackNode, TrackEdge>> options) {
        java.util.List<Couple<TrackNode>> route = turnMemory.route();
        if (route != null) {
            for (Couple<TrackNode> leg : route) {
                TrackEdge edge = g.getConnection(leg);
                for (java.util.Map.Entry<TrackNode, TrackEdge> entry : options) {
                    if (entry.getValue() == edge) {
                        return entry;
                    }
                }
            }
        }
        Vec3 goal = turnMemory.intent();
        return goal == null ? null : bestByGoal(options, goal, false);
    }

    private java.util.Map.Entry<TrackNode, TrackEdge> recordTurn(java.util.Map.Entry<TrackNode, TrackEdge> entry) {
        if (turnLog != null && turnLeader) {
            turnLog.record(entry.getKey());
            turnCursor = turnLog.head();
        }
        return entry;
    }

    private ITrackSelector replaySelector(TravellingPoint point) {
        return (g, pair) -> {
            java.util.List<java.util.Map.Entry<TrackNode, TrackEdge>> targets = pair.getSecond();
            TrackNode want = turnLog.at(turnCursor);
            if (want != null) {
                turnCursor++;
                for (java.util.Map.Entry<TrackNode, TrackEdge> entry : targets) {
                    if (entry.getKey() == want) {
                        return entry;
                    }
                }
            }
            if (targets.size() == 1) {
                return targets.get(0);
            }
            if (followTarget != null) {
                return point.follow(followTarget).apply(g, pair);
            }
            return targets.get(0);
        };
    }

    public void setTurnLog(TurnLog log, boolean leader) {
        if (this.turnLog != log) {
            this.turnLog = log;
            this.turnCursor = log == null ? 0L : log.head();
        }
        if (this.turnLeader != leader) {
            this.turnLeader = leader;
            this.turnCursor = log == null ? 0L : log.head();
        }
    }

    public long turnCursor() {
        return turnCursor;
    }

    public void setSteerTarget(Vec3 worldTarget) {
        this.steerTarget = worldTarget;
    }

    public Vec3 steerTarget() {
        return steerTarget;
    }

    private ITrackSelector pathSelector() {
        return (g, pair) -> {
            java.util.List<java.util.Map.Entry<TrackNode, TrackEdge>> options = pair.getSecond();
            if (path == null || path.isEmpty()) {
                return recordTurn(options.get(0));
            }
            // the selector only fires at real junctions, so the head of the route is stale by every straight hop since
            for (int i = 0; i < path.size(); i++) {
                TrackEdge targetEdge = g.getConnection(path.get(i));
                for (java.util.Map.Entry<TrackNode, TrackEdge> entry : options) {
                    if (entry.getValue() == targetEdge) {
                        path.subList(0, i + 1).clear();
                        return recordTurn(entry);
                    }
                }
            }
            return recordTurn(options.get(0));
        };
    }

    public List<net.createmod.catnip.data.Couple<TrackNode>> remainingPath() {
        return path;
    }

    public void setPath(List<net.createmod.catnip.data.Couple<TrackNode>> route) {
        this.path = route;
    }

    public void clearPath() {
        this.path = null;
    }

    public void recenterOn(TravellingPoint seed, TrackGraph seedGraph) {
        if (seed == null || seed.edge == null) {
            return;
        }
        seatSpan(seed, seedGraph, bogeySpacing / 2.0);
        stopped = false;
        reachedEnd = false;
        pendingPortal = null;
    }

    private static void copyPoint(TravellingPoint dst, TravellingPoint src) {
        dst.node1 = src.node1;
        dst.node2 = src.node2;
        dst.edge = src.edge;
        dst.position = src.position;
        dst.upsideDown = src.upsideDown;
        dst.blocked = false;
    }

    public TravellingPoint leading() {
        return leading;
    }

    public TravellingPoint trailing() {
        return trailing;
    }

    public boolean isOnCurve() {
        return (leading != null && leading.edge != null && leading.edge.isTurn())
                || (trailing != null && trailing.edge != null && trailing.edge.isTurn());
    }

    public boolean isOnMonorail() {
        return onMonorail(leading) || onMonorail(trailing);
    }

    private static boolean onMonorail(TravellingPoint point) {
        return point != null && point.edge != null
                && LoconauticsConstants.RAILWAYS_MONORAIL_MATERIAL.equals(point.edge.getTrackMaterial().id);
    }

    public TrackGraph graph() {
        return graph;
    }

    public ServerSubLevel activeSub() {
        return isFar(leading) ? null : hostOf(leading);
    }

    public Vec3 upNormal() {
        return upNormal;
    }

    public Vec3 leadingPos() {
        return worldPos(leading);
    }

    public Vec3 leadingRailPos() {
        return railPointOf(leading);
    }

    private Vec3 railPointOf(TravellingPoint p) {
        if (p == null || p.edge == null) {
            return null;
        }
        double len = p.edge.getLength();
        if (len < 1.0e-9) {
            return null;
        }
        Vec3 v = p.edge.getPosition(graph, p.position / len);
        Pose3dc pose = poseOf(p);
        if (v == null) {
            return null;
        }
        if (pose != null) {
            Vector3d w = pose.transformPosition(new Vector3d(v.x, v.y, v.z), new Vector3d());
            v = new Vec3(w.x, w.y, w.z);
        }
        return toHome(p.edge, v);
    }

    public record PointFrame(Vector3d c, Vector3d n, Vector3d t) {
    }

    public TrackMaterial trackMaterial() {
        TravellingPoint p = leading != null && leading.edge != null ? leading : trailing;
        return p == null || p.edge == null ? null : p.edge.getTrackMaterial();
    }

    // the track point under this span, its own normal and a tangent turned to agree with the reference
    public PointFrame railFrame(Vector3d reference) {
        PointFrame a = pointFrame(leading, reference);
        PointFrame b = pointFrame(trailing, reference);
        if (a == null || b == null) {
            return null;
        }
        Vector3d n = new Vector3d(a.n()).add(b.n());
        Vector3d t = new Vector3d(a.t()).add(b.t());
        if (n.lengthSquared() < 1.0e-9 || t.lengthSquared() < 1.0e-9) {
            return null;
        }
        t.normalize();
        n.sub(new Vector3d(t).mul(n.dot(t)));
        if (n.lengthSquared() < 1.0e-9) {
            return null;
        }
        return new PointFrame(new Vector3d(a.c()).add(b.c()).mul(0.5), n.normalize(), t);
    }

    private PointFrame pointFrame(TravellingPoint p, Vector3d reference) {
        Vec3 c = railPointOf(p);
        if (c == null) {
            return null;
        }
        double len = p.edge.getLength();
        Vec3 normal = p.edge.getNormal(graph, p.position / len);
        Vec3 dir = p.edge.getDirectionAt(p.position);
        if (normal == null || dir == null || dir.lengthSqr() < 1.0e-9) {
            return null;
        }
        Pose3dc pose = poseOf(p);
        Vector3d n = new Vector3d(normal.x, normal.y, normal.z);
        if (pose != null) {
            pose.transformNormal(n);
        }
        if (isFarEdge(p.edge)) {
            n = portal.dirToNear(n);
        }
        if (p.upsideDown) {
            n.negate();
        }
        Vec3 worldDir = homeDir(p.edge, dir);
        Vector3d t = new Vector3d(worldDir.x, worldDir.y, worldDir.z);
        if (t.lengthSquared() < 1.0e-9) {
            return null;
        }
        t.normalize();
        if (t.dot(reference) < 0.0) {
            t.negate();
        }
        return new PointFrame(new Vector3d(c.x, c.y, c.z), n.normalize(), t);
    }

    public Vec3 trailingPos() {
        return worldPos(trailing);
    }

    public Vector3d center() {
        Vec3 a = worldPos(leading);
        Vec3 b = worldPos(trailing);
        if (a == null || b == null) {
            return null;
        }
        return new Vector3d((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
    }

    public Quaterniond orientation() {
        return orientationTo(forwardVec());
    }

    // world frame, sabletraindriver.updatebogeyyaw cancels the body out of it before syncing
    public Quaterniond chordFrameQuaternion() {
        Vec3 fw = forward();
        Vector3d f = new Vector3d(fw.x, fw.y, fw.z);
        if (f.lengthSquared() < 1.0e-9) {
            return new Quaterniond();
        }
        f.normalize();
        Vector3d up = perpendicularUp(f, upRef());
        return basis(f, up).getNormalizedRotation(new Quaterniond()).normalize();
    }

    public Quaterniond leadingTangentQuaternion() {
        Quaterniond q = tangentQuaternion(leading);
        return q == null ? new Quaterniond() : q;
    }

    private Quaterniond tangentQuaternion(TravellingPoint point) {
        if (point == null || point.edge == null) {
            return null;
        }
        Vec3 d = point.edge.getDirectionAt(point.position);
        if (d == null || d.lengthSqr() < 1.0e-9) {
            return null;
        }
        Pose3dc pose = poseOf(point);
        Vector3d f = new Vector3d(d.x, d.y, d.z).normalize();
        Vec3 worldTan = homeDir(point.edge, d);
        Vec3 fwd = forward();
        boolean agree = worldTan != null && (worldTan.x * fwd.x + worldTan.z * fwd.z) >= 0.0;
        if (!agree) {
            f.negate();
        }
        Vector3d up = perpendicularUp(f);
        Quaterniond q = basis(f, up).getNormalizedRotation(new Quaterniond()).normalize();
        if (pose != null) {
            q = new Quaterniond(pose.orientation()).mul(q).normalize();
        }
        return isFarEdge(point.edge) ? portal.orientToNear(q) : q;
    }

    public Vector3d referenceForward() {
        return forward0;
    }

    public Vector3d referenceUp() {
        return up0;
    }

    public Quaterniond orientationTo(Vector3d forward) {
        if (forward0 == null) {
            captureReference();
            if (forward0 == null) {
                return new Quaterniond();
            }
        }
        if (forward == null || forward.lengthSquared() < 1.0e-9) {
            return new Quaterniond();
        }
        Vector3d f = new Vector3d(forward).normalize();
        Vector3d up = perpendicularUp(f, upRef());
        Matrix3d ref = basis(forward0, up0);
        Matrix3d now = basis(f, up);
        Matrix3d delta = now.mul(ref.transpose(new Matrix3d()), new Matrix3d());
        return delta.getNormalizedRotation(new Quaterniond()).normalize();
    }

    private void captureReference() {
        Vector3d f = forwardVec();
        if (f != null) {
            this.forward0 = Math.abs(f.x) >= Math.abs(f.z)
                    ? new Vector3d(Math.signum(f.x), 0, 0)
                    : new Vector3d(0, 0, Math.signum(f.z));
            this.up0 = perpendicularUp(this.forward0);
        }
    }

    private Vector3d forwardVec() {
        Vec3 pa = worldPos(leading);
        Vec3 pb = worldPos(trailing);
        if (pa == null || pb == null) {
            return null;
        }
        Vector3d a = new Vector3d(pa.x, pa.y, pa.z);
        Vector3d b = new Vector3d(pb.x, pb.y, pb.z);
        Vector3d f = a.sub(b, new Vector3d());
        if (f.lengthSquared() < 1.0e-9) {
            return new Vector3d(1, 0, 0);
        }
        return f.normalize();
    }



    private Vector3d upRef() {
        Pose3dc pose = poseOf(leading);
        Vector3d local = restUp();
        Vector3d up = pose == null ? local : pose.transformNormal(local, new Vector3d());
        return isFar(leading) ? portal.dirToNear(up) : up;
    }

    // the tracks own normal in the frame it was laid in, which for a sub level is its plot, never the world
    private Vector3d restUp() {
        return upNormal == null || upNormal.lengthSqr() < 1.0e-9
                ? new Vector3d(0, 1, 0)
                : new Vector3d(upNormal.x, upNormal.y, upNormal.z).normalize();
    }

    public Vec3 railUp() {
        Vector3d f = forwardVec();
        Vector3d up = f == null ? upRef() : perpendicularUp(f, upRef());
        return new Vec3(up.x, up.y, up.z);
    }

    public double hostTilt() {
        Pose3dc pose = poseOf(leading);
        if (pose == null) {
            return 0.0;
        }
        Vector3d rest = restUp();
        Vector3d now = pose.transformNormal(new Vector3d(rest), new Vector3d());
        double dot = now.dot(rest);
        return Math.acos(dot < -1.0 ? -1.0 : (dot > 1.0 ? 1.0 : dot));
    }

    private static Matrix3d basis(Vector3d forward, Vector3d up) {
        Vector3d right = new Vector3d(up).cross(forward);
        Matrix3d m = new Matrix3d();
        m.setColumn(0, right);
        m.setColumn(1, up);
        m.setColumn(2, forward);
        return m;
    }

    public Vec3 forward() {
        Vec3 f = leadingPos().subtract(trailingPos());
        double len = f.length();
        return len < 1.0e-6 ? new Vec3(1, 0, 0) : f.scale(1.0 / len);
    }

    public Vec3 localEdgeTangent() {
        if (leading == null || leading.edge == null) {
            return null;
        }
        Vec3 d = leading.edge.isInterDimensional() ? null : leading.edge.getDirectionAt(leading.position);
        return d == null || d.lengthSqr() < 1.0e-9 ? null : d.normalize();
    }

    public Vec3 edgeTangent() {
        if (leading == null || leading.edge == null) {
            return null;
        }
        Vec3 d = leading.edge.isInterDimensional() ? null : leading.edge.getDirectionAt(leading.position);
        if (d == null || d.lengthSqr() < 1.0e-9) {
            return null;
        }
        Vec3 w = homeDir(leading.edge, d);
        return (w == null || w.lengthSqr() < 1.0e-9) ? null : w.normalize();
    }

    public double signedLateralCurvature() {
        if (leading == null || leading.edge == null) {
            return 0.0;
        }
        if (leading.edge.isInterDimensional()) {
            return 0.0;
        }
        Vec3 t0 = homeDir(leading.edge, leading.edge.getDirectionAt(leading.position));
        if (t0 == null || t0.lengthSqr() < 1.0e-9) {
            return 0.0;
        }
        t0 = t0.normalize();
        double ds = 0.5;
        double len = leading.edge.getLength();
        double ahead = leading.position + ds <= len ? leading.position + ds : leading.position - ds;
        Vec3 t1 = homeDir(leading.edge, leading.edge.getDirectionAt(ahead));
        if (t1 == null || t1.lengthSqr() < 1.0e-9) {
            return 0.0;
        }
        t1 = t1.normalize();
        Vec3 dT = t1.subtract(t0).scale(1.0 / ds);
        Vec3 lateral = railUp().cross(t0);
        if (lateral.lengthSqr() < 1.0e-9) {
            return 0.0;
        }
        return dT.dot(lateral.normalize());
    }

    public boolean stopped() {
        return stopped;
    }

    public void unstick() {
        stopped = false;
    }

    public double lastTravelSign() {
        return lastTravelSign;
    }

    public void setLastTravelSign(double sign) {
        this.lastTravelSign = sign;
    }

    public TravellingPoint followTarget() {
        return followTarget;
    }

    // a bogey on another network turns on its own nodes, it never follows the others and never counts as a pivot
    public boolean isIndependent() {
        return independent;
    }

    public void setIndependent(boolean independent) {
        this.independent = independent;
        if (independent) {
            followTarget = null;
        }
    }

    public void followPoint(TravellingPoint target) {
        if (target == null || target == followTarget) {
            return;
        }
        followTarget = target;
    }

    public void clearFollowPoint() {
        followTarget = null;
    }

    public void adoptReferenceFrame(RailCarriage from) {
        if (from != null && from.forward0 != null) {
            this.forward0 = new Vector3d(from.forward0);
            this.up0 = from.up0 != null ? new Vector3d(from.up0) : perpendicularUp(this.forward0);
        }
    }

    public boolean edgesValid() {
        return rebind();
    }

    private boolean rebind() {
        TrackEdge leadingBefore = leading.edge;
        TrackEdge trailingBefore = trailing.edge;
        boolean leadingOk = pointValid(leading);
        boolean trailingOk = pointValid(trailing);
        if (leadingOk && trailingOk && (leading.edge != leadingBefore || trailing.edge != trailingBefore)) {
            remapRoute();
        }
        return leadingOk && trailingOk;
    }

    // the route and the turn log hold node objects from the old graph, and a lookup by identity misses them
    private void remapRoute() {
        if (path != null) {
            for (int i = 0; i < path.size(); i++) {
                Couple<TrackNode> hop = path.get(i);
                TrackNode first = liveNode(hop.getFirst());
                TrackNode second = liveNode(hop.getSecond());
                if (first != hop.getFirst() || second != hop.getSecond()) {
                    path.set(i, Couple.create(first, second));
                }
            }
        }
        if (turnLog != null) {
            turnLog.remap(this::liveNode);
        }
    }

    private TrackNode liveNode(TrackNode node) {
        TrackNode live = node == null ? null : graph.locateNode(node.getLocation());
        return live == null ? node : live;
    }

    private boolean pointValid(TravellingPoint point) {
        if (point == null || point.node1 == null || point.node2 == null || point.edge == null) {
            return false;
        }
        adoptLiveGraph();
        TrackNode n1 = graph.locateNode(point.node1.getLocation());
        if (n1 == null) {
            return false;
        }
        point.node1 = n1;
        var connections = graph.getConnectionsFrom(point.node1);
        TrackEdge edge = connections.get(point.node2);
        if (edge == null) {
            TrackNode n2 = graph.locateNode(point.node2.getLocation());
            if (n2 == null || (edge = connections.get(n2)) == null) {
                return false;
            }
            point.node2 = n2;
        }
        point.edge = edge;
        point.position = Math.min(point.position, edge.getLength());
        return true;
    }

    private void adoptLiveGraph() {
        var networks = com.simibubi.create.Create.RAILWAYS.trackNetworks;
        if (graph == null || networks == null) {
            return;
        }
        TrackNodeLocation at = leading != null && leading.node1 != null ? leading.node1.getLocation() : null;
        if (at == null) {
            return;
        }
        // a split keeps the old graph id registered, so the id alone never tells you the object is still yours
        if (networks.containsKey(graph.id) && graph.locateNode(at) != null) {
            return;
        }
        for (TrackGraph candidate : networks.values()) {
            if (candidate.locateNode(at) != null) {
                graph = candidate;
                return;
            }
        }
    }

    public double bogeySpacing() {
        return bogeySpacing;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setTravelIntent(double intent) {
        this.travelIntent = intent;
    }

    public UUID graphId() {
        return graph.id;
    }

    public CompoundTag writeNbt(DimensionPalette dims) {
        if (forward0 == null) {
            captureReference();
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Leading", leading.write(dims));
        tag.put("Trailing", trailing.write(dims));
        tag.putDouble("Spacing", bogeySpacing);
        tag.putBoolean("Stopped", stopped);
        tag.putBoolean("Independent", independent);
        tag.putDouble("UpX", upNormal.x);
        tag.putDouble("UpY", upNormal.y);
        tag.putDouble("UpZ", upNormal.z);
        if (forward0 != null) {
            tag.putDouble("FwdX", forward0.x);
            tag.putDouble("FwdY", forward0.y);
            tag.putDouble("FwdZ", forward0.z);
        }
        if (portal != null) {
            tag.put("PortalNear", portal.nearLoc().write(dims));
            tag.put("PortalFar", portal.farLoc().write(dims));
        }
        return tag;
    }

    public static RailCarriage restore(TrackGraph graph, CompoundTag tag, DimensionPalette dims) {
        TravellingPoint leading = TravellingPoint.read(tag.getCompound("Leading"), graph, dims);
        TravellingPoint trailing = TravellingPoint.read(tag.getCompound("Trailing"), graph, dims);
        if (leading.edge == null || trailing.edge == null) {
            return null;
        }
        Vec3 up = new Vec3(tag.getDouble("UpX"), tag.getDouble("UpY"), tag.getDouble("UpZ"));
        double spacing = tag.getDouble("Spacing");
        RailCarriage c = new RailCarriage(graph, leading, trailing, up, spacing, 0.0);
        if (tag.contains("FwdX")) {
            c.forward0 = new Vector3d(tag.getDouble("FwdX"), tag.getDouble("FwdY"), tag.getDouble("FwdZ"));
            c.up0 = perpendicularUp(c.forward0);
        }
        c.stopped = tag.getBoolean("Stopped");
        c.independent = tag.getBoolean("Independent");
        if (tag.contains("PortalNear") && tag.contains("PortalFar")) {
            TrackNodeLocation near = TrackNodeLocation.read(tag.getCompound("PortalNear"), dims);
            TrackNodeLocation far = TrackNodeLocation.read(tag.getCompound("PortalFar"), dims);
            c.portal = PortalFrame.of(graph, Couple.create(near, far), near.dimension);
            if (c.portal == null) {
                LoconauticsConstants.LOGGER.warn("[portal] saved portal frame {} to {} could not be rebuilt on restore, "
                        + "graph={}", near.dimension.location(), far.dimension.location(), graph != null);
            }
        }
        return c;
    }
}
