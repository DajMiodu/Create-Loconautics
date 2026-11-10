package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.signal.SignalSnap;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class RailDebug {

    private static final Map<UUID, UUID> SELECTED = new HashMap<>();

    private RailDebug() {
    }

    public static void clearSelections() {
        SELECTED.clear();
    }

    private static UUID selectedOf(ServerPlayer player) {
        return SELECTED.get(player.getUUID());
    }

    public static void addBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root
                .then(Commands.literal("derail")
                        .executes(ctx -> SableTrainSpawner.derailLookedAt(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("hop")
                        .then(Commands.literal("where")
                                .executes(ctx -> hopWhere(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("signal")
                        .then(Commands.literal("offset")
                                .executes(ctx -> showSignalOffset(ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("blocks", FloatArgumentType.floatArg(-16.0f, 16.0f))
                                        .executes(ctx -> setSignalOffset(ctx.getSource().getPlayerOrException(),
                                                FloatArgumentType.getFloat(ctx, "blocks")))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> resetSignal(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("station")
                        .then(stopOffsetBranch("offset"))
                        .then(stopOffsetBranch("end"))
                        .then(Commands.literal("reset")
                                .executes(ctx -> resetStation(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("ghost")
                        .then(Commands.literal("status")
                                .executes(ctx -> ghostStatus(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("force")
                                .executes(ctx -> forceGhost(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("schedule")
                        .then(Commands.literal("select")
                                .executes(ctx -> select(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("resume")
                                .executes(ctx -> resume(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stop(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clear(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("status")
                                .executes(ctx -> stationStatus(ctx.getSource().getPlayerOrException()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stopOffsetBranch(String name) {
        return Commands.literal(name)
                .executes(ctx -> showStopOffset(ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("blocks", FloatArgumentType.floatArg(-16.0f, 16.0f))
                        .executes(ctx -> setStopOffset(ctx.getSource().getPlayerOrException(),
                                FloatArgumentType.getFloat(ctx, "blocks"))));
    }

    private static int hopWhere(ServerPlayer player) {
        UUID selected = selectedOf(player);
        SableTrain train = selected == null ? SableTrainSpawner.pickLookedAtTrain(player)
                : SableTrainRegistry.get(selected);
        if (train == null) {
            return fail(player, "look at a train or select one first");
        }
        ok(player, "hop probe for " + shortId(train.car().subLevelId())
                + ", green marks a rail it can take, red marks a follower that is stuck");
        for (String line : TrainRailAdvance.hopReport(train)) {
            ok(player, line);
        }
        return 1;
    }

    private static int forceGhost(ServerPlayer player) {
        UUID selected = selectedOf(player);
        SableTrain train = selected == null ? SableTrainSpawner.pickLookedAtTrain(player)
                : SableTrainRegistry.get(selected);
        if (train == null) {
            return fail(player, "look at a train or select one first");
        }
        return ok(player, com.lycoris.loconautics.trainphysics.state.TrainResidency.forceGhost(train));
    }

    private static int ghostStatus(ServerPlayer player) {
        int ghosts = 0;
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.isGhost()) {
                ghosts++;
            }
            ok(player, com.lycoris.loconautics.trainphysics.state.TrainResidency.describe(train)
                    + " " + com.lycoris.loconautics.trainphysics.state.StationChunkTickets.describe(train));
        }
        ok(player, com.lycoris.loconautics.trainphysics.state.StationChunkTickets.summary());
        return ok(player, ghosts + " ghost(s) of " + SableTrainRegistry.all().size() + " train(s)");
    }

    private static int showStopOffset(ServerPlayer player) {
        return ok(player, "station park offset is " + StationLanding.parkOffset
                + " (positive parks short of the marker, the green column sits on it)");
    }

    private static int setStopOffset(ServerPlayer player, float blocks) {
        StationLanding.parkOffset = blocks;
        return ok(player, "station park offset set to " + blocks + ", the green column moves with it");
    }

    private static int resetStation(ServerPlayer player) {
        StationLanding.parkOffset = StationLanding.DEFAULT_PARK_OFFSET;
        return ok(player, "station park offset back to the baked value, " + StationLanding.parkOffset);
    }

    private static int resetSignal(ServerPlayer player) {
        SignalSnap.stopOffset = SignalSnap.DEFAULT_STOP_OFFSET;
        return ok(player, "signal stop offset back to the baked value, " + SignalSnap.stopOffset);
    }

    private static int showSignalOffset(ServerPlayer player) {
        return ok(player, "signal stop offset is " + SignalSnap.stopOffset
                + " (how far short of the boundary the nose parks)");
    }

    private static int setSignalOffset(ServerPlayer player, float blocks) {
        SignalSnap.stopOffset = blocks;
        return ok(player, "signal stop offset set to " + blocks + ", takes effect on the next approach");
    }

    private static int select(ServerPlayer player) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "look at a train to select it");
        }
        SELECTED.put(player.getUUID(), train.id());
        return scheduleOk(player, "selected train " + shortId(train.id()));
    }

    private static int stationStatus(ServerPlayer player) {
        UUID selected = selectedOf(player);
        if (selected == null) {
            return scheduleFail(player, "select a train first");
        }
        var runtime = SableScheduleRuntime.get(SableScheduleRuntime.consistKey(selected));
        if (runtime == null) {
            return scheduleFail(player, "no station schedule on " + shortId(selected));
        }
        String held = runtime.heldReason() == null ? "running" : "held, " + runtime.heldReason();
        return scheduleOk(player, "entry " + (runtime.currentEntry + 1) + "/" + runtime.schedule.entries.size()
                + ", " + runtime.state + ", " + held
                + (runtime.schedule.cyclic ? ", cyclic" : ""));
    }

    private static int resume(ServerPlayer player) {
        UUID selected = selectedOf(player);
        if (selected == null) {
            return scheduleFail(player, "select a train first");
        }
        return scheduleOk(player, SableScheduleRuntime.resume(selected)
                ? "schedule resumed" : "nothing paused to resume");
    }

    private static int stop(ServerPlayer player) {
        UUID selected = selectedOf(player);
        if (selected == null) {
            return scheduleFail(player, "select a train first");
        }
        UUID key = SableScheduleRuntime.consistKey(selected);
        var runtime = SableScheduleRuntime.get(key);
        if (runtime == null) {
            return scheduleFail(player, "no schedule on " + shortId(selected));
        }
        runtime.paused = true;
        releaseHold(key);
        return scheduleOk(player, "schedule paused");
    }

    private static int clear(ServerPlayer player) {
        UUID selected = selectedOf(player);
        if (selected == null) {
            return scheduleFail(player, "select a train first");
        }
        UUID key = SableScheduleRuntime.consistKey(selected);
        SableScheduleRuntime.clear(key);
        releaseHold(key);
        return scheduleOk(player, "schedule cleared");
    }

    private static void releaseHold(UUID selected) {
        SableTrain train = SableTrainRegistry.get(selected);
        if (train != null) {
            train.setScheduleHold(false);
        }
    }

    private static int ok(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 1;
    }

    private static int fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 0;
    }

    private static int scheduleOk(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 1;
    }

    private static int scheduleFail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 0;
    }
}
