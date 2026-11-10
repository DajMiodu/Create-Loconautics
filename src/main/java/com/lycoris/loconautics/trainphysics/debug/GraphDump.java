package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.RouteDistance;
import com.lycoris.loconautics.content.schedule.SchedulePathfinder;
import com.lycoris.loconautics.content.schedule.SchedulePlanner;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

public final class GraphDump {

    private GraphDump() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("graph")
                .executes(ctx -> dump(ctx.getSource().getPlayerOrException())));
    }

    private static int dump(ServerPlayer player) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "no train under the crosshair");
        }
        RailCarriage rail = TrainStations.noseRail(train);
        TravellingPoint nose = TrainStations.nosePoint(train);
        TrackGraph graph = rail == null ? null : rail.graph();
        if (graph == null || nose == null || nose.edge == null) {
            return fail(player, "train has no track graph or no rail point");
        }

        line(player, "graph " + graph.id.toString().substring(0, 8)
                + " speed " + String.format("%.4f", train.speed())
                + " nose " + RouteDistance.describe(nose));
        int sense = SableTrainDriver.noseSense(train, nose);
        line(player, "  node1 " + at(nose.node1) + " node2 " + at(nose.node2)
                + " (positive travel runs node1 to node2)");
        line(player, "  locomotive front points " + (sense > 0 ? "node1 to node2, nose sense +1"
                : "node2 to node1, nose sense -1"));

        Collection<GlobalStation> stations = graph.getPoints(EdgePointType.STATION);
        line(player, stations.size() + " station(s) in this graph");
        for (GlobalStation station : stations) {
            TrackGraphLocation loc = SchedulePlanner.locationOf(graph, station);
            TrackNode a = graph.locateNode(loc.edge.getFirst());
            TrackNode b = graph.locateNode(loc.edge.getSecond());
            line(player, "station \"" + station.name + "\" pos " + String.format("%.2f", station.position)
                    + (station.assembling ? " ASSEMBLING" : ""));
            line(player, "  edge first " + at(a) + " second " + at(b)
                    + ", legal approach heads into second");
            if (a == null || b == null) {
                line(player, "  one edge node is missing from the graph");
                continue;
            }
            line(player, "  approachFrom first=" + station.canApproachFrom(a)
                    + " second=" + station.canApproachFrom(b));
            double ahead = SchedulePlanner.aheadOnCurrentEdge(nose, a, b, loc);
            line(player, "  aheadOnCurrentEdge " + (Double.isNaN(ahead) ? "NaN (not on this edge, or wrong side)"
                    : String.format("%.2f", ahead)));
            line(player, "  ahead of the nose " + describe(
                    SchedulePathfinder.findAhead(graph, nose, a, b, sense, true)));
            line(player, "  behind the nose   " + describe(
                    SchedulePathfinder.findAhead(graph, nose, a, b, -sense, true)));
            line(player, "  wrong side too    " + describe(
                    SchedulePathfinder.findAhead(graph, nose, a, b, sense, false)));
        }
        return 1;
    }

    private static String describe(SchedulePathfinder.Path path) {
        if (path == null) {
            return "no path";
        }
        return String.format("dir %+d dist %.2f, %s",
                path.direction(), path.distance(), RouteDistance.describeRoute(path.edges()));
    }

    private static String at(TrackNode node) {
        if (node == null) {
            return "null";
        }
        Vec3 v = node.getLocation().getLocation();
        return String.format("%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }

    private static void line(ServerPlayer player, String text) {
        LoconauticsConstants.LOGGER.info("[graphdump] {}", text);
        player.sendSystemMessage(Component.literal(text));
    }

    private static int fail(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
        return 0;
    }
}
