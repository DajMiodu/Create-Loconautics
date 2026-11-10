package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public final class AxleDebugCommand {

    private AxleDebugCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> axle = Commands.literal("axle")
                .executes(ctx -> info(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("consist")
                        .executes(ctx -> consist(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("bogeys")
                        .executes(ctx -> bogeys(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("front")
                        .then(Commands.literal("clear")
                                .executes(ctx -> setFront(ctx.getSource().getPlayerOrException(), null))));
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            axle.then(Commands.literal("front")
                    .then(Commands.literal(dir.getSerializedName())
                            .executes(ctx -> setFront(ctx.getSource().getPlayerOrException(), dir))));
        }
        root.then(axle);
    }

    private static int info(ServerPlayer player) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "no train under the crosshair");
        }
        ConsistPower.Result power = ConsistPower.resultFor(train);
        if (power == null || power.axles().isEmpty()) {
            return fail(player, "no valid Bearing Axle in this consist");
        }
        for (ConsistPower.Axle axle : power.axles()) {
            int group = axle.group();
            send(player, String.format(
                    "%s axle %s bogey %s strength %.1f%s share %.0f%s%s",
                    shortId(axle.subLevelId()), pos(axle.axleLocalPos()), pos(axle.bogeyLocalPos()),
                    axle.signedStrength(), group < 0 ? " reverse" : " forward",
                    power.share(group), power.isMinority(group) ? " opposed" : "",
                    axle.powered() ? "" : " no kinetic network"));
        }
        return 1;
    }

    private static int consist(ServerPlayer player) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "no train under the crosshair");
        }
        ConsistPower.Result power = ConsistPower.resultFor(train);
        if (power == null) {
            return fail(player, "this train has not been solved yet");
        }
        send(player, String.format(
                "cars %d, axles %d fwd / %d rev, strength %.1f fwd / %.1f rev, net %.1f, slip %.0f%%, target %.4f b/t, mass %.0f",
                power.memberCount(), power.forwardCount(), power.reverseCount(),
                power.forwardStrength(), power.reverseStrength(), power.netStrength(),
                (1.0 - power.slipFactor()) * 100.0, power.targetSpeedBpt(), power.totalMass()));
        return 1;
    }

    private static int bogeys(ServerPlayer player) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "no train under the crosshair");
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
        if (sub == null) {
            return fail(player, "this car's body is not loaded");
        }
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            BlockState state = sub.getLevel().getBlockState(
                    sub.getPlot().getCenterBlock().offset(bogey.localPos()));
            Direction override = train.bogeyFrontOverride(bogey.localPos());
            Direction front = BogeyFacing.front(state, override);
            send(player, String.format("bogey %s axis %s front %s%s",
                    pos(bogey.localPos()),
                    BogeyFacing.axisOf(state) == null ? "none" : BogeyFacing.axisOf(state).getName(),
                    front == null ? "none" : front.getSerializedName(),
                    override == null ? " (default)" : " (set)"));
        }
        return 1;
    }

    private static int setFront(ServerPlayer player, Direction front) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "no train under the crosshair");
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
        if (sub == null) {
            return fail(player, "this car's body is not loaded");
        }
        SableTrain.Bogey bogey = nearestBogey(train, player);
        if (bogey == null) {
            return fail(player, "this car has no bogey to set");
        }
        BlockState state = sub.getLevel().getBlockState(
                sub.getPlot().getCenterBlock().offset(bogey.localPos()));
        if (front != null && !BogeyFacing.onAxis(state, front)) {
            return fail(player, "that bogey runs along " + BogeyFacing.axisOf(state)
                    + ", " + front.getSerializedName() + " is off its axis");
        }
        train.setBogeyFrontOverride(bogey.localPos(), front);
        send(player, "bogey " + pos(bogey.localPos()) + " front "
                + (front == null ? "back to the axis default" : front.getSerializedName()));
        return 1;
    }

    private static SableTrain.Bogey nearestBogey(SableTrain train, ServerPlayer player) {
        SableTrain.Bogey best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            Vector3d center = bogey.rail() == null ? null : bogey.rail().center();
            if (center == null) {
                continue;
            }
            double distance = center.distanceSquared(
                    player.getX(), player.getEyeY(), player.getZ());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = bogey;
            }
        }
        return best;
    }

    private static String pos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }

    private static void send(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[axle] " + text));
    }

    private static int fail(ServerPlayer player, String text) {
        send(player, text);
        return 0;
    }
}
