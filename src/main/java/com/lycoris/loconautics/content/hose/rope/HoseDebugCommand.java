package com.lycoris.loconautics.content.hose.rope;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class HoseDebugCommand {

    private HoseDebugCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("hose")
                .executes(ctx -> toggle(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("lines")
                        .executes(ctx -> toggleLines(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("joint")
                        .executes(ctx -> toggleJoint(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("status")
                        .executes(ctx -> say(ctx.getSource().getPlayerOrException(), HoseRopeTrace.status())))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource().getPlayerOrException()))));
    }

    private static int toggle(ServerPlayer player) {
        boolean on = HoseRopeTrace.toggleEnabled();
        return say(player, on
                ? "hose trace on, one [hosetrace] line per rope per tick in latest.log"
                : "hose trace off");
    }

    private static int toggleLines(ServerPlayer player) {
        boolean on = HoseRopeTrace.toggleLines();
        return say(player, on
                ? "hose arrows on, red pulls a body, gray goes to the world, cyan is the body's velocity change, magenta is the damping joint"
                : "hose arrows off");
    }

    private static int toggleJoint(ServerPlayer player) {
        boolean off = HoseRopeTrace.toggleJoint();
        return say(player, off
                ? "hose damping joint DISABLED, ropes lose the joint between their two bodies"
                : "hose damping joint active");
    }

    private static int reset(ServerPlayer player) {
        HoseRopeTrace.reset();
        return say(player, "hose trace, arrows and the joint switch back to their defaults");
    }

    private static int say(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 1;
    }
}
