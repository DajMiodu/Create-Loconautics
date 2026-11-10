package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PortalTrace {

    private static volatile boolean on;

    private static final Map<Object, Long> LAST = new HashMap<>();

    private PortalTrace() {
    }

    public static boolean on() {
        return on;
    }

    public static void log(String message, Object... args) {
        if (on) {
            LoconauticsConstants.LOGGER.info("[portal/trace] " + message, args);
        }
    }

    public static String id(UUID id) {
        return id == null ? "none" : id.toString().substring(0, 8);
    }

    public static String f(double value) {
        return String.format("%.3f", value);
    }

    public static boolean due(Object key, long now, long interval) {
        if (!on) {
            return false;
        }
        Long last = LAST.get(key);
        if (last != null && now - last < interval) {
            return false;
        }
        LAST.put(key, now);
        return true;
    }

    public static boolean dueAlways(Object key, long now, long interval) {
        Long last = LAST.get(key);
        if (last != null && now - last < interval) {
            return false;
        }
        LAST.put(key, now);
        return true;
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> debug) {
        debug.then(Commands.literal("portal").then(Commands.literal("trace")
                .executes(ctx -> set(ctx.getSource(), !on))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> set(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))));
    }

    private static int set(CommandSourceStack source, boolean value) {
        on = value;
        LAST.clear();
        source.sendSuccess(() -> Component.literal("portal trace " + (on ? "on" : "off")), false);
        return 1;
    }
}
