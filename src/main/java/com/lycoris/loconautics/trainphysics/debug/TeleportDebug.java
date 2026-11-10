package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.teleport.TrainTeleporter;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TeleportDebug {

    private TeleportDebug() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("teleport")
                .then(Commands.argument("targets", SubLevelArgumentType.subLevels())
                        .then(Commands.literal("rail")
                                .executes(TeleportDebug::toRail))
                        .then(Commands.argument("destination", Vec3Argument.vec3(false))
                                .executes(ctx -> drop(ctx, null, null))
                                .then(Commands.argument("angle", RotationArgument.rotation())
                                        .executes(ctx -> drop(ctx, null,
                                                RotationArgument.getRotation(ctx, "angle"))))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(ctx -> drop(ctx,
                                                DimensionArgument.getDimension(ctx, "dimension"), null))
                                        .then(Commands.argument("angle", RotationArgument.rotation())
                                                .executes(ctx -> drop(ctx,
                                                        DimensionArgument.getDimension(ctx, "dimension"),
                                                        RotationArgument.getRotation(ctx, "angle"))))))));
    }

    private static int drop(CommandContext<CommandSourceStack> ctx, @Nullable ServerLevel dimension,
                            @Nullable Coordinates angle) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Vec3 position = Vec3Argument.getVec3(ctx, "destination");
        ServerLevel destLevel = dimension == null ? source.getLevel() : dimension;
        int moved = 0;
        for (Target target : targets(ctx)) {
            if (target.sub().isRemoved()) {
                trace(source, "body " + shortId(target.sub().getUniqueId()) + " is no longer live");
                continue;
            }
            double yaw = angle == null ? SubLevelData.yawDeg(target.sub()) : angle.getRotation(source).y;
            if (target.train() == null) {
                moved += dropBody(source, target.sub(), destLevel, position, yaw);
                continue;
            }
            TrainTeleporter.Result result = TrainTeleporter.teleport(target.train(), destLevel, position, yaw,
                    line -> trace(source, line));
            moved += report(source, target.train(), result);
        }
        return moved;
    }

    private static int dropBody(CommandSourceStack source, ServerSubLevel sub, ServerLevel destLevel, Vec3 position,
                                double yaw) {
        SableTeleportation.Result result = SableTeleportation.teleport(sub, destLevel,
                new Vector3d(position.x, position.y + SableTeleportation.baseOffsetY(sub), position.z),
                new Quaterniond().rotateY(Math.toRadians(yaw)));
        String id = shortId(sub.getUniqueId());
        if (result.ok()) {
            trace(source, "body " + id + " landed in " + destLevel.dimension().location());
            return 1;
        }
        trace(source, "body " + id + " refused: " + result.failure());
        return 0;
    }

    private static int toRail(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        SableTrainSpawner.RailSeat seat = SableTrainSpawner.lookedAtRailSeat(player);
        if (seat == null) {
            trace(source, "look at a piece of track first");
            return 0;
        }
        int moved = 0;
        for (Target target : targets(ctx)) {
            if (target.train() == null) {
                trace(source, "body " + shortId(target.sub().getUniqueId()) + " is not a train, it has no rail state");
                continue;
            }
            TrainTeleporter.Result result = TrainTeleporter.teleportToRail(target.train(), player.serverLevel(),
                    seat.location(), seat.upNormal(), seat.lookAngle(), line -> trace(source, line));
            if (result.ok()) {
                for (SableTrain car : result.cars()) {
                    car.setSpeed(0.0);
                    car.setTargetSpeed(0.0);
                }
            }
            moved += report(source, target.train(), result);
        }
        return moved;
    }

    private record Target(@Nullable SableTrain train, ServerSubLevel sub) {}

    private static List<Target> targets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Set<UUID> seen = new HashSet<>();
        List<Target> out = new ArrayList<>();
        for (ServerSubLevel sub : SubLevelArgumentType.getSubLevels(ctx, "targets")) {
            SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
            if (train == null) {
                out.add(new Target(null, sub));
                continue;
            }
            List<SableTrain> chain = SableTrainSpawner.chainCars(train.level(), train.car().subLevelId());
            boolean known = false;
            for (SableTrain car : chain) {
                known |= seen.contains(car.id());
            }
            if (known || !seen.add(train.id())) {
                continue;
            }
            for (SableTrain car : chain) {
                seen.add(car.id());
            }
            out.add(new Target(train, sub));
        }
        return out;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static int report(CommandSourceStack source, SableTrain train, TrainTeleporter.Result result) {
        String id = shortId(train.id());
        if (result.ok()) {
            trace(source, "car " + id + " moved a chain of " + result.moved());
            return result.moved();
        }
        trace(source, "car " + id + " refused: " + result.failure() + ", " + result.moved() + " car(s) had moved");
        return 0;
    }

    private static void trace(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal("[tp] " + line), false);
    }
}
