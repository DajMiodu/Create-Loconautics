package com.lycoris.loconautics.content.knuckle;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class KnuckleTuning {

    public static final double DEFAULT_CONNECT_DISTANCE = 1.5;
    public static final double DEFAULT_MATE_OPEN_GAP = 2.5;
    public static final double DEFAULT_MATE_CLOSE_GAP = 2.05;
    public static final double DEFAULT_POSITION_LERP = 0.35;
    public static final double DEFAULT_SPRING_STIFFNESS = 20000.0;
    public static final double DEFAULT_MAX_BIAS_SPEED = 1.0;

    public static final double REST_LENGTH = 2.0;
    public static final int ENGAGE_TICKS = 8;
    public static final int ROTATE_LOCK_TICKS = 40;
    public static final int COUPLE_LOCK_TICKS = 60;

    public static final double MAX_ACCEL = 60.0;
    public static final double WATCH_REACH = 6.0;
    public static final double BREAK_DISTANCE = 8.0;
    public static final int MISSING_PARTNER_SCANS = 4;

    public static final double BAUMGARTE = 0.2;
    public static final double SLOP = 0.002;
    public static final double A_MIN = 0.2;
    public static final int SWEEPS = 8;
    public static final double MAX_DS = 40.0;
    public static final double DEEP_ERROR_START = 0.3;
    public static final double DEEP_ERROR_SPAN = 0.5;
    public static final double DEEP_BIAS_SPEED = 6.0;

    public static final double FRONT_SLACK = 0.25;
    public static final double OPPOSED_DOT = -0.4;
    public static final double MAX_OFFSET = 0.3;
    public static final double MAX_TILT_SIN = 0.05;

    public static final float CLIP_LENGTH = 2.0f;

    public static double connectDistance = DEFAULT_CONNECT_DISTANCE;

    public static double mateOpenGap = DEFAULT_MATE_OPEN_GAP;
    public static double mateCloseGap = DEFAULT_MATE_CLOSE_GAP;

    public static double positionLerp = DEFAULT_POSITION_LERP;

    public static boolean trace;
    public static double springStiffness = DEFAULT_SPRING_STIFFNESS;
    public static double maxBiasSpeed = DEFAULT_MAX_BIAS_SPEED;

    private KnuckleTuning() {
    }

    public static float clipForGap(double hingeGap) {
        double span = mateOpenGap - mateCloseGap;
        if (span <= 1.0e-6) {
            return hingeGap <= mateCloseGap ? CLIP_LENGTH : 0.0f;
        }
        return (float) (Mth.clamp((mateOpenGap - hingeGap) / span, 0.0, 1.0) * CLIP_LENGTH);
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("knuckle")
                .then(value("hitbox", 0.1, 6.0, () -> connectDistance, v -> connectDistance = v))
                .then(value("open", 0.1, 8.0, () -> mateOpenGap, v -> mateOpenGap = v))
                .then(value("close", 0.1, 8.0, () -> mateCloseGap, v -> mateCloseGap = v))
                .then(value("lerp", 0.0, 4.0, () -> positionLerp, v -> positionLerp = v))
                .then(value("spring", 100.0, 1000000.0, () -> springStiffness, v -> springStiffness = v))
                .then(value("bias", 0.05, 10.0, () -> maxBiasSpeed, v -> maxBiasSpeed = v))
                .then(Commands.literal("trace")
                        .executes(ctx -> {
                            trace = !trace;
                            return report(ctx.getSource(), "trace " + (trace ? "on" : "off"));
                        }))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            connectDistance = DEFAULT_CONNECT_DISTANCE;
                            mateOpenGap = DEFAULT_MATE_OPEN_GAP;
                            mateCloseGap = DEFAULT_MATE_CLOSE_GAP;
                            positionLerp = DEFAULT_POSITION_LERP;
                            springStiffness = DEFAULT_SPRING_STIFFNESS;
                            maxBiasSpeed = DEFAULT_MAX_BIAS_SPEED;
                            return report(ctx.getSource(), "knuckle tuning reset");
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> value(String name, double min, double max,
                                                                    DoubleSupplier get,
                                                                    DoubleConsumer set) {
        return Commands.literal(name)
                .executes(ctx -> report(ctx.getSource(), name + " = " + fmt(get.getAsDouble())))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max))
                        .executes(ctx -> {
                            set.accept(DoubleArgumentType.getDouble(ctx, "value"));
                            return report(ctx.getSource(), name + " = " + fmt(get.getAsDouble()));
                        }));
    }

    static String fmt(double v) {
        return String.format("%.3f", v);
    }

    private static int report(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[knuckle] " + message), false);
        return 1;
    }
}
