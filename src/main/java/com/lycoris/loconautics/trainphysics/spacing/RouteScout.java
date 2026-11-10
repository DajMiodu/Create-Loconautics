package com.lycoris.loconautics.trainphysics.spacing;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RouteScout {

    static final double STEP = 1.0;

    record Scan(double free, Footprint hit, BlockPos block) {}

    private RouteScout() {
    }

    static Scan scan(SableTrain train, Footprint self, double sign, double look, List<Footprint> obstacles,
                     ServerLevel blockLevel) {
        SableTrain.Car car = train.car();
        List<SableTrain.Bogey> chain = car.chain();
        RailCarriage lead = chain.isEmpty() ? car.carriage() : (sign >= 0.0 ? chain.get(chain.size() - 1) : chain.get(0)).rail();
        if (lead == null || lead.graph() == null) {
            return null;
        }
        Vec3 bodyForward = chain.isEmpty() ? null : TrainRailAdvance.bogeyChordForward(chain);
        if (bodyForward == null) {
            bodyForward = lead.forward();
        }
        double dirX = bodyForward.x * (sign >= 0.0 ? 1.0 : -1.0);
        double dirZ = bodyForward.z * (sign >= 0.0 ? 1.0 : -1.0);
        Vec3 leadPos = lead.leadingPos();
        Vec3 trailPos = lead.trailingPos();
        if (leadPos == null || trailPos == null) {
            return null;
        }
        boolean leadingIsFront = leadPos.x * dirX + leadPos.z * dirZ >= trailPos.x * dirX + trailPos.z * dirZ;
        TravellingPoint front = leadingIsFront ? lead.leading() : lead.trailing();
        Vec3 frontPos = leadingIsFront ? leadPos : trailPos;
        if (front == null || front.edge == null || !readable(front.edge)) {
            return null;
        }
        Vec3 tangent = front.edge.getDirectionAt(front.position);
        double along = tangent == null ? 0.0 : tangent.x * dirX + tangent.z * dirZ;
        if (Math.abs(along) < 1.0e-3) {
            return null;
        }
        double travel = along > 0.0 ? STEP : -STEP;
        double overhang = Math.max(0.0, self.halfLength() - Math.hypot(frontPos.x - self.x(), frontPos.z - self.z()));
        TrackGraph graph = lead.graph();
        TravellingPoint scout = new TravellingPoint(front.node1, front.node2, front.edge, front.position,
                front.upsideDown);
        ITrackSelector selector = sign >= 0.0 && lead.remainingPath() != null && !lead.remainingPath().isEmpty()
                ? routeSelector(lead.remainingPath()) : scout.steer(SteerDirection.NONE, lead.upNormal());
        List<Footprint> ahead = aheadOf(obstacles, frontPos, dirX, dirZ, overhang);
        double walked = 0.0;
        boolean leftStart = blockLevel == null;
        while (walked <= look) {
            if (scout.edge.getLength() >= 1.0e-9) {
                Vec3 at = scout.getPosition(graph);
                Footprint hit = hitAt(ahead, at, overhang);
                if (hit != null) {
                    return new Scan(Math.max(0.0, walked - STEP), hit, null);
                }
                if (blockLevel != null) {
                    boolean blocked = GhostObstacles.isBlocked(blockLevel, at);
                    if (blocked && leftStart) {
                        return new Scan(Math.max(0.0, walked - STEP - overhang), null, BlockPos.containing(at));
                    }
                    leftStart |= !blocked;
                }
            }
            double moved = scout.travel(graph, travel, selector, scout.ignoreEdgePoints(), scout.ignoreTurns(),
                    scout.ignorePortals());
            if (Math.abs(moved) < STEP * 0.5 || scout.edge == null || !readable(scout.edge)) {
                break;
            }
            walked += Math.abs(moved);
        }
        return new Scan(Double.POSITIVE_INFINITY, null, null);
    }

    private static List<Footprint> aheadOf(List<Footprint> obstacles, Vec3 frontPos, double dirX, double dirZ,
                                           double overhang) {
        List<Footprint> ahead = new ArrayList<>(obstacles.size());
        for (Footprint obstacle : obstacles) {
            boolean covering = obstacle.covers(frontPos.x, frontPos.y, frontPos.z, overhang);
            boolean behind = (obstacle.x() - frontPos.x) * dirX + (obstacle.z() - frontPos.z) * dirZ <= 0.0;
            if (!covering || !behind) {
                ahead.add(obstacle);
            }
        }
        return ahead;
    }

    // rails on a moving body or across a portal live in another frame
    private static boolean readable(TrackEdge edge) {
        return !edge.isInterDimensional() && edge.node1 != null && RailSeams.hostIdOf(edge.node1.getLocation()) == null;
    }

    private static Footprint hitAt(List<Footprint> obstacles, Vec3 at, double overhang) {
        for (Footprint obstacle : obstacles) {
            if (obstacle.covers(at.x, at.y, at.z, overhang)) {
                return obstacle;
            }
        }
        return null;
    }

    private static ITrackSelector routeSelector(List<Couple<TrackNode>> path) {
        return (graph, pair) -> {
            List<Map.Entry<TrackNode, TrackEdge>> options = pair.getSecond();
            for (Couple<TrackNode> step : path) {
                TrackEdge wanted = graph.getConnection(step);
                for (Map.Entry<TrackNode, TrackEdge> option : options) {
                    if (option.getValue() == wanted) {
                        return option;
                    }
                }
            }
            return options.get(0);
        };
    }
}
