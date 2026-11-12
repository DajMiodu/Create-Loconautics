package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackPropagator;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;


public final class SableRailHop {
    static final double HOP_MAX_ANGLE_DEGREES = 72.5;


    public record Result(TrackGraph graph, TrackGraphLocation location, ServerSubLevel sub, Vec3 railPoint) {
    }

    private static final double SEARCH_RADIUS = 3.0;
    private static final double ORIENT_SCORE_WEIGHT = 0.04;

    // must sample ahead of the bogey, or a railhead just past the gap fails the projection test and the train parks at the seam forever
    private static final double[] PROBE_STEPS = { 0.0, 0.75, 1.5, 2.25, 3.0 };
    private static final double[] NO_SWEEP = { 0.0 };

    private SableRailHop() {
    }

    public record Probe(Result hit, double step, String detail) {
    }

    public static Probe probe(ServerLevel level, TrackGraph fromGraph, ServerSubLevel fromSub,
                              ServerSubLevel selfBody, Vec3 worldPos, Vec3 worldDir, Vec3 worldVert,
                              double maxOffset, boolean sweepAhead) {
        if (level == null || worldPos == null || worldDir == null) {
            return null;
        }
        Vec3 dir = worldDir.lengthSqr() > 1.0e-9 ? worldDir.normalize() : new Vec3(1, 0, 0);
        for (double step : (sweepAhead ? PROBE_STEPS : NO_SWEEP)) {
            Vec3 at = worldPos.add(dir.scale(step));
            Result r = scan(level, fromSub, selfBody, at, worldDir, worldVert, fromGraph, maxOffset);
            if (r != null) {
                return new Probe(r, step, "");
            }
        }
        return new Probe(null, 0.0, "no rail in range");
    }

    private static final double[] AHEAD_STEPS = { 1.0, 2.0, 3.0 };

    public static boolean ownGraphTrackAhead(ServerLevel level, TrackGraph graph, ServerSubLevel host,
                                             Vec3 worldPos, Vec3 worldDir) {
        if (level == null || graph == null || worldPos == null || worldDir == null
                || worldDir.lengthSqr() < 1.0e-9) {
            return false;
        }
        Vec3 dir = worldDir.normalize();
        Vec3 localAt = host == null ? worldPos : toLocalPos(host, worldPos);
        Vec3 localDir = host == null ? dir : toLocalDir(host, dir).normalize();
        BlockPos standingOn = BlockPos.containing(localAt);
        for (double step : AHEAD_STEPS) {
            Vec3 probe = localAt.add(localDir.scale(step));
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos pos = BlockPos.containing(probe).above(dy);
                if (pos.equals(standingOn) || !isOwnGraphTrack(level, graph, pos, localDir)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isOwnGraphTrack(ServerLevel level, TrackGraph graph, BlockPos pos, Vec3 localDir) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ITrackBlock track)) {
            return false;
        }
        Pair<Vec3, Direction.AxisDirection> axis = track.getNearestTrackAxis(level, pos, state, localDir);
        TrackGraphLocation loc = TrackGraphHelper.getGraphLocationAt(level, pos, axis.getSecond(), axis.getFirst());
        if (loc == null || loc.graph == null) {
            TrackPropagator.onRailAdded(level, pos, state);
            return true;
        }
        return loc.graph == graph;
    }

    private static final class Best {
        double score = Double.MAX_VALUE;
        TrackGraphLocation location;
        ServerSubLevel sub;
        Vec3 railPoint;
    }

    private static Result scan(ServerLevel level, ServerSubLevel fromSub, ServerSubLevel selfBody, Vec3 worldPos,
                               Vec3 worldDir, Vec3 worldVert, TrackGraph excludeGraph, double maxOffset) {
        if (level == null || worldPos == null || worldDir == null) {
            return null;
        }
        Vec3 dir = worldDir.lengthSqr() > 1.0e-9 ? worldDir.normalize() : new Vec3(1, 0, 0);
        Vec3 vert = (worldVert != null && worldVert.lengthSqr() > 1.0e-9) ? worldVert.normalize() : new Vec3(0, 1, 0);
        double radius = Math.max(SEARCH_RADIUS, maxOffset + 1.0);
        BoundingBox3d bb = new BoundingBox3d(
                worldPos.x - radius, worldPos.y - radius, worldPos.z - radius,
                worldPos.x + radius, worldPos.y + radius, worldPos.z + radius);


        Best best = new Best();
        scanHost(level, null, worldPos, dir, vert, excludeGraph, maxOffset, best);
        for (SubLevel sl : Sable.HELPER.getAllIntersecting(level, bb)) {
            if (!(sl instanceof ServerSubLevel ssl) || ssl.isRemoved()) {
                continue;
            }
            // never ride the graph we are already on, nor the cars own hull it would grab its own cargo rails
            if (ssl == fromSub || ssl == selfBody) {
                continue;
            }
            scanHost(level, ssl, worldPos, dir, vert, excludeGraph, maxOffset, best);
        }
        if (best.location == null) {
            return null;
        }
        return new Result(best.location.graph, best.location, best.sub, best.railPoint);
    }

    private static double maxOrientScore() {
        return 1.0 - Math.cos(Math.toRadians(HOP_MAX_ANGLE_DEGREES));
    }

    // all positionsdirections below are in the hosts plot frame world  identity, so world coords pass through
    private static void scanHost(ServerLevel level, ServerSubLevel host, Vec3 worldPos, Vec3 worldDir, Vec3 worldVert,
                                 TrackGraph excludeGraph, double maxOffset, Best best) {
        Vec3 localPos;
        Vec3 localDir;
        Vec3 localVert;
        if (host == null) {
            localPos = worldPos;
            localDir = worldDir;
            localVert = worldVert;
        } else {
            localPos = toLocalPos(host, worldPos);
            localDir = toLocalDir(host, worldDir);
            localVert = toLocalDir(host, worldVert);
        }
        double maxOffsetSq = maxOffset * maxOffset;

        for (int x = Mth.floor(localPos.x) - 1; x <= Mth.ceil(localPos.x) + 1; x++) {
            for (int y = Mth.floor(localPos.y) - 2; y <= Mth.ceil(localPos.y) + 2; y++) {
                for (int z = Mth.floor(localPos.z) - 1; z <= Mth.ceil(localPos.z) + 1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ITrackBlock track)) {
                        continue;
                    }
                    Pair<Vec3, Direction.AxisDirection> axis = track.getNearestTrackAxis(level, pos, state,
                            new Vec3(localDir.x, localDir.y, localDir.z));
                    Vec3 delta = axis.getFirst();
                    double deltaLen = delta.length();
                    if (deltaLen < 1.0e-6) {
                        continue;
                    }
                    TrackGraphLocation loc = TrackGraphHelper.getGraphLocationAt(level, pos, axis.getSecond(), delta);
                    if (loc == null || loc.graph == null) {
                        // graph stale nudge create to rebuild, try again next tick
                        TrackPropagator.onRailAdded(level, pos, state);
                        continue;
                    }
                    if (excludeGraph != null && loc.graph == excludeGraph) {
                        continue;
                    }
                    Vec3 vertN = track.getUpNormal(level, pos, state).normalize();
                    Vec3 lat = delta.cross(vertN).normalize();
                    Vec3 center = new Vec3(pos.getX() + 0.5,
                            pos.getY() + track.getElevationAtCenter(level, pos, state),
                            pos.getZ() + 0.5);
                    Vec3 start = center.subtract(delta.scale(0.5));

                    if (!inSegmentRange(start, delta, vertN, lat, localPos, localVert)) {
                        continue;
                    }
                    double orientScore = 1.0 - Math.abs(localDir.dot(delta) / deltaLen);
                    if (orientScore > maxOrientScore()) {
                        continue;
                    }
                    double distScore = distanceSqLinePoint(center, delta, localPos);
                    if (distScore > maxOffsetSq) {
                        continue;
                    }
                    double score = distScore + ORIENT_SCORE_WEIGHT * orientScore;
                    if (score >= best.score) {
                        continue;
                    }
                    best.score = score;
                    best.location = loc;
                    best.sub = host;
                    best.railPoint = host == null ? center : toWorldPos(host, center);
                }
            }
        }
    }

    private static Vec3 toLocalPos(ServerSubLevel sub, Vec3 world) {
        return com.lycoris.loconautics.trainphysics.sublevel.SablePlotFrame.toLocalPos(sub, world);
    }

    private static Vec3 toWorldPos(ServerSubLevel sub, Vec3 local) {
        return com.lycoris.loconautics.trainphysics.sublevel.SablePlotFrame.toWorldPos(sub, local);
    }

    private static Vec3 toLocalDir(ServerSubLevel sub, Vec3 world) {
        return com.lycoris.loconautics.trainphysics.sublevel.SablePlotFrame.toLocalDir(sub, world);
    }

    private static boolean inSegmentRange(Vec3 start, Vec3 delta, Vec3 vert, Vec3 lat, Vec3 localPos, Vec3 localVert) {
        return inProjectionRange(start, delta, vert, localPos, localVert)
                && inLineRange(start, vert, lat, localPos, localVert);
    }

    private static boolean inLineRange(Vec3 start, Vec3 vert, Vec3 lat, Vec3 localPos, Vec3 localVert) {
        if (vert.dot(localVert) < 0.5) {
            return false;
        }
        double vDist = projT(start, vert, localPos);
        if (vDist < -1.5 || vDist > 0.375) {
            return false;
        }
        double lDist = projT(start, lat, localPos);
        return lDist >= -0.5 && lDist <= 0.5;
    }

    private static boolean inProjectionRange(Vec3 start, Vec3 delta, Vec3 vert, Vec3 localPos, Vec3 localVert) {
        double t = intersectPlaneLine(localPos, localVert, start, vert);
        if (Double.isNaN(t)) {
            return false;
        }
        Vec3 i = localPos.add(localVert.scale(t));
        double projT = projT(start, delta, i);
        return projT >= -1.0 / 32.0 && projT <= 1.0 + 1.0 / 32.0;
    }

    private static double projT(Vec3 start, Vec3 dir, Vec3 p) {
        double d2 = dir.lengthSqr();
        return d2 < 1.0e-12 ? 0.0 : p.subtract(start).dot(dir) / d2;
    }

    private static double distanceSqLinePoint(Vec3 center, Vec3 delta, Vec3 p) {
        double t = projT(center, delta, p);
        Vec3 perp = p.subtract(center).subtract(delta.scale(t));
        return perp.lengthSqr();
    }

    private static double intersectPlaneLine(Vec3 lp, Vec3 ld, Vec3 pp, Vec3 pn) {
        double denom = ld.dot(pn);
        if (Math.abs(denom) < 1.0e-9) {
            return Double.NaN;
        }
        return pp.subtract(lp).dot(pn) / denom;
    }
}
