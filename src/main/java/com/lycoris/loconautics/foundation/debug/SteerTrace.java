package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SteerTrace {

    private static final Map<UUID, String> LAST_MODE = new HashMap<>();
    private static final Map<Integer, String> OWNERS = new HashMap<>();
    private static boolean enabled;

    private SteerTrace() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void mode(UUID car, String mode) {
        if (enabled && !mode.equals(LAST_MODE.put(car, mode))) {
            LoconauticsConstants.LOGGER.info("[steer] car {} mode {}", car.toString().substring(0, 8), mode);
        }
    }

    public static void owner(Object carriage, UUID car) {
        if (enabled) {
            OWNERS.put(System.identityHashCode(carriage), car.toString().substring(0, 8));
        }
    }

    public static void pick(Object carriage, String how, int options, String picked) {
        if (enabled) {
            int id = System.identityHashCode(carriage);
            LoconauticsConstants.LOGGER.info("[steer] junction {} car {} {} of {} options, {}",
                    Integer.toHexString(id), OWNERS.getOrDefault(id, "?"), how, options, picked);
        }
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("steer").then(Commands.literal("trace").executes(ctx -> {
            enabled = !enabled;
            LAST_MODE.clear();
            OWNERS.clear();
            ctx.getSource().sendSuccess(() -> Component.literal("steer trace " + (enabled ? "on" : "off")), false);
            return 1;
        })));
    }
}
