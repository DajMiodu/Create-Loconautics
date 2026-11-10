package com.lycoris.loconautics.trainphysics.debug.force;

import com.lycoris.loconautics.config.DiagnosticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ForceDebugCommand {

    private ForceDebugCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("force")
                .executes(ctx -> toggle(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("lines")
                        .executes(ctx -> toggleLines(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("why")
                        .executes(ctx -> dump(ctx.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("top")
                        .executes(ctx -> dump(ctx.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("calibrate")
                        .executes(ctx -> calibrate(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("threshold")
                        .executes(ctx -> showThresholds(ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("blocksPerSecond", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .executes(ctx -> setChat(ctx.getSource().getPlayerOrException(),
                                        DoubleArgumentType.getDouble(ctx, "blocksPerSecond")))))
                .then(Commands.literal("anomaly")
                        .then(Commands.argument("blocksPerSecond", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .executes(ctx -> setAnomaly(ctx.getSource().getPlayerOrException(),
                                        DoubleArgumentType.getDouble(ctx, "blocksPerSecond")))))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource().getPlayerOrException()))));
    }

    private static int toggle(ServerPlayer player) {
        ForceTrace.chat = !ForceTrace.chat;
        if (!ForceTrace.chat) {
            return ok(player, "force chat off, the log still dumps every anomaly");
        }
        return ok(player, "force chat on, every push over " + ForceTrace.chatDeltaV
                + " b/s is announced and a tick over " + ForceTrace.anomalyDeltaV + " b/s dumps its whole ledger");
    }

    private static int toggleLines(ServerPlayer player) {
        DiagnosticsConfig.Channel.FORCE_LINES.toggle();
        return ok(player, "force arrows " + (ForceTrace.lines()
                ? "on, each push draws an arrow at where it was applied, colored by source"
                : "off"));
    }

    private static int dump(ServerPlayer player, boolean worst) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "look at a train first");
        }
        for (String line : worst ? ForceTrace.top(train) : ForceTrace.why(train)) {
            ok(player, line);
        }
        return 1;
    }

    private static int calibrate(ServerPlayer player) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "look at a train first");
        }
        ok(player, "commanded against delivered, a ratio far off 1 means the impulse unit is wrong");
        for (String line : ForceTrace.calibration(train)) {
            ok(player, line);
        }
        return 1;
    }

    private static int showThresholds(ServerPlayer player) {
        return ok(player, "chat at " + ForceTrace.chatDeltaV + " b/s per source, anomaly dump at "
                + ForceTrace.anomalyDeltaV + " b/s a tick or " + ForceTrace.anomalySpin
                + " rad/s of spin");
    }

    private static int setChat(ServerPlayer player, double value) {
        ForceTrace.chatDeltaV = value;
        return ok(player, "force chat threshold set to " + value + " b/s per source per tick");
    }

    private static int setAnomaly(ServerPlayer player, double value) {
        ForceTrace.anomalyDeltaV = value;
        return ok(player, "force anomaly threshold set to " + value + " b/s in one tick");
    }

    private static int reset(ServerPlayer player) {
        ForceTrace.reset();
        return ok(player, "force ledgers cleared and every threshold back to its baked value");
    }

    private static int ok(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 1;
    }

    private static int fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 0;
    }
}
