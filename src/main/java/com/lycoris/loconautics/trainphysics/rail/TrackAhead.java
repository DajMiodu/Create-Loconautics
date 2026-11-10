package com.lycoris.loconautics.trainphysics.rail;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TrackAhead {

    public static final double DEFAULT_RANGE = 256.0;
    public static final double MAX_RANGE = 1024.0;
    public static final double DEFAULT_SLOPE_DELTA = 1.0;

    private static final double GAP_RANGE = 12.0;
    private static final double MIN_CURVE_DEGREES = 1.0;
    private static final int MAX_PIECES = 4096;

    private TrackAhead() {
    }

    // host null is world rail, a seam piece carries the host it leads into
    public record Piece(double start, double length, UUID host, boolean seam, boolean turn,
                        double slopeStart, double slopeEnd, double turnDegrees, Vec3 at) {
    }

    public record End(double distance, Vec3 world, UUID host) {
    }

    public record Gap(double distance, double gap, UUID host, Vec3 world) {
    }

    public record Report(UUID startHost, List<Piece> pieces, End end, Gap gap) {
    }

    public record Curve(double distance, double length, double degrees, double radius, UUID host) {
    }

    public record SlopeChange(double distance, double from, double to) {
    }

    public record TrackChange(double distance, UUID from, UUID to, boolean joined, double gap, Vec3 world) {
    }

    public static Report scan(SableTrain train, double range) {
        if (train.isDerailed() || train.isPhysics() || train.car() == null) {
            return null;
        }
        RailCarriage carriage = TrainStations.noseRail(train);
        TravellingPoint nose = TrainStations.nosePoint(train);
        TrackGraph graph = carriage == null ? null : carriage.graph();
        if (graph == null || nose == null || nose.edge == null) {
            return null;
        }
        boolean forward = SableTrainDriver.noseSense(train, nose) > 0;
        TrackNode from = forward ? nose.node1 : nose.node2;
        TrackNode to = forward ? nose.node2 : nose.node1;
        TrackEdge edge = forward ? nose.edge : graph.getConnectionsFrom(from).get(to);
        if (edge == null) {
            return null;
        }
        double at = Mth.clamp(forward ? nose.position : nose.edge.getLength() - nose.position,
                0.0, edge.getLength());

        UUID startHost = hostOf(from);
        List<Piece> pieces = new ArrayList<>();
        double walked = 0.0;
        double slope = Double.NaN;
        Vec3 out = null;
        for (int i = 0; i < MAX_PIECES && walked < range; i++) {
            if (edge.isInterDimensional()) {
                break;
            }
            boolean seam = RailSeams.isSeam(from.getLocation(), to.getLocation());
            double run = Math.max(0.0, edge.getLength() - at);
            Piece piece = seam ? seamPiece(walked, run, to, slope) : railPiece(edge, from, at, walked, run, slope);
            pieces.add(piece);
            if (!seam) {
                slope = piece.slopeEnd();
                out = worldDir(from, edge.getDirection(false));
            }
            walked += run;

            Map.Entry<TrackNode, TrackEdge> next = straightest(graph, from, to, edge, out);
            if (next == null) {
                End end = new End(walked, RailSeams.worldPosOf(to.getLocation()), hostOf(to));
                return new Report(startHost, pieces, end, gapBeyond(train, graph, end, out));
            }
            from = to;
            to = next.getKey();
            edge = next.getValue();
            at = 0.0;
        }
        return new Report(startHost, pieces, null, null);
    }

    private static Piece railPiece(TrackEdge edge, TrackNode from, double at, double start, double run,
                                   double carriedSlope) {
        double length = edge.getLength();
        if (length < 1.0e-6) {
            return new Piece(start, run, hostOf(from), false, false, carriedSlope, carriedSlope, 0.0, null);
        }
        Vec3 head = worldDir(from, edge.getDirectionAt(at));
        Vec3 tail = worldDir(from, edge.getDirectionAt(length));
        double degrees = edge.isTurn() ? yawDegrees(head, tail) : 0.0;
        return new Piece(start, run, hostOf(from), false, edge.isTurn(),
                slopeOf(head, carriedSlope), slopeOf(tail, carriedSlope), degrees, null);
    }

    private static Piece seamPiece(double start, double run, TrackNode to, double carriedSlope) {
        return new Piece(start, run, hostOf(to), true, false, carriedSlope, carriedSlope, 0.0,
                RailSeams.worldPosOf(to.getLocation()));
    }

    private static Map.Entry<TrackNode, TrackEdge> straightest(TrackGraph graph, TrackNode from, TrackNode to,
                                                               TrackEdge edge, Vec3 out) {
        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(to);
        if (connections == null) {
            return null;
        }
        Map.Entry<TrackNode, TrackEdge> best = null;
        double bestDot = -Double.MAX_VALUE;
        for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
            if (entry.getKey() == from || !edge.canTravelTo(entry.getValue())) {
                continue;
            }
            Vec3 in = worldDir(to, entry.getValue().getDirection(true));
            double dot = out == null || in == null ? 0.0 : out.dot(in);
            if (dot > bestDot) {
                bestDot = dot;
                best = entry;
            }
        }
        return best;
    }

    private static Gap gapBeyond(SableTrain train, TrackGraph graph, End end, Vec3 along) {
        if (end.world() == null || along == null || train.level() == null) {
            return null;
        }
        ServerSubLevel self = SubLevelData.resolve(train.level(), train.car().subLevelId());
        ServerSubLevel endHost = end.host() == null ? null : SubLevelData.resolve(train.level(), end.host());
        Vec3 up = new Vec3(0, 1, 0);
        for (double gap = 0.0; gap <= GAP_RANGE; gap += 1.0) {
            SableRailHop.Probe probe = SableRailHop.probe(train.level(), graph, endHost, self,
                    end.world().add(along.scale(gap)), along, up, 1.0, false);
            SableRailHop.Result hit = probe == null ? null : probe.hit();
            if (hit != null) {
                UUID host = hit.sub() == null ? null : hit.sub().getUniqueId();
                return new Gap(end.distance() + gap, gap, host, hit.railPoint());
            }
        }
        return null;
    }

    public static Double slopeAt(Report report, double distance) {
        for (Piece piece : report.pieces()) {
            if (distance > piece.start() + piece.length() || Double.isNaN(piece.slopeStart())) {
                continue;
            }
            double t = piece.length() < 1.0e-6 ? 0.0 : (distance - piece.start()) / piece.length();
            return piece.slopeStart() + (piece.slopeEnd() - piece.slopeStart()) * Mth.clamp(t, 0.0, 1.0);
        }
        return null;
    }

    public static SlopeChange nextSlopeChange(Report report, double minDelta) {
        Double base = slopeAt(report, 0.0);
        if (base == null) {
            return null;
        }
        for (Piece piece : report.pieces()) {
            if (Double.isNaN(piece.slopeStart())) {
                continue;
            }
            double startOff = piece.slopeStart() - base;
            double endOff = piece.slopeEnd() - base;
            if (Math.abs(startOff) >= minDelta) {
                return new SlopeChange(piece.start(), base, piece.slopeStart());
            }
            if (Math.abs(endOff) >= minDelta) {
                double t = (Math.signum(endOff) * minDelta - startOff) / (endOff - startOff);
                return new SlopeChange(piece.start() + piece.length() * Mth.clamp(t, 0.0, 1.0), base,
                        piece.slopeEnd());
            }
        }
        return null;
    }

    public static Curve nextCurve(Report report) {
        List<Piece> pieces = report.pieces();
        for (int i = 0; i < pieces.size(); i++) {
            Piece first = pieces.get(i);
            if (!first.turn() || Math.abs(first.turnDegrees()) < MIN_CURVE_DEGREES) {
                continue;
            }
            double length = 0.0;
            double degrees = 0.0;
            for (int j = i; j < pieces.size(); j++) {
                Piece piece = pieces.get(j);
                if (!piece.turn() || Math.signum(piece.turnDegrees()) != Math.signum(first.turnDegrees())) {
                    break;
                }
                length += piece.length();
                degrees += piece.turnDegrees();
            }
            double radians = Math.toRadians(Math.abs(degrees));
            return new Curve(first.start(), length, degrees, radians < 1.0e-6 ? 0.0 : length / radians,
                    first.host());
        }
        return null;
    }

    public static TrackChange nextChange(Report report, boolean toSubLevel) {
        UUID current = report.startHost();
        for (Piece piece : report.pieces()) {
            if (!piece.seam() || Objects.equals(piece.host(), current)) {
                continue;
            }
            UUID before = current;
            current = piece.host();
            if (toSubLevel ? current != null && !current.equals(report.startHost()) : current == null) {
                return new TrackChange(piece.start(), before, current, true, 0.0, piece.at());
            }
        }
        Gap gap = report.gap();
        if (gap != null && !Objects.equals(gap.host(), current)
                && (toSubLevel ? gap.host() != null : gap.host() == null)) {
            return new TrackChange(gap.distance(), current, gap.host(), false, gap.gap(), gap.world());
        }
        return null;
    }

    public static TrackChange firstChange(Report report) {
        UUID current = report.startHost();
        for (Piece piece : report.pieces()) {
            if (piece.seam() && !Objects.equals(piece.host(), current)) {
                return new TrackChange(piece.start(), current, piece.host(), true, 0.0, piece.at());
            }
        }
        Gap gap = report.gap();
        if (gap != null && !Objects.equals(gap.host(), current)) {
            return new TrackChange(gap.distance(), current, gap.host(), false, gap.gap(), gap.world());
        }
        return null;
    }

    private static UUID hostOf(TrackNode node) {
        ServerSubLevel host = RailSeams.serverHostOf(node.getLocation());
        return host == null ? null : host.getUniqueId();
    }

    private static Vec3 worldDir(TrackNode node, Vec3 direction) {
        return RailSeams.worldDirOf(node.getLocation(), direction);
    }

    private static double slopeOf(Vec3 direction, double fallback) {
        return direction == null ? fallback : Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1.0, 1.0)));
    }

    // positive turns right seen from above, the same sign the route steps use
    private static double yawDegrees(Vec3 from, Vec3 to) {
        if (from == null || to == null) {
            return 0.0;
        }
        double cross = from.x * to.z - from.z * to.x;
        double dot = from.x * to.x + from.z * to.z;
        return Math.toDegrees(Math.atan2(cross, dot));
    }
}
