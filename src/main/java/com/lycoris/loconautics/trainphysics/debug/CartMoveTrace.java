package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

// temporary, tag cartmove
public final class CartMoveTrace {

    private static final int MAX_LINES = 6000;
    private static final double JUMP_SLACK = 0.5;

    private static final Map<UUID, Vec3> LAST_CENTER = new HashMap<>();
    private static int lines;

    private CartMoveTrace() {
    }

    public static void step(SableTrain train, double ds) {
        if (lines >= MAX_LINES || train.car() == null || train.car().carriage() == null || !train.isScheduleDriven()) {
            return;
        }
        var rail = train.car().carriage();
        Vec3 center = rail.moveCenter();
        Vec3 before = LAST_CENTER.put(train.id(), center);
        double moved = before == null || center == null ? 0.0 : center.distanceTo(before);
        boolean jump = moved > Math.abs(ds) + JUMP_SLACK;
        if (!jump && Math.abs(ds) < 1.0e-9 && moved < 0.01) {
            return;
        }
        lines++;
        LoconauticsConstants.LOGGER.info("[cartmove] {} tick={} {} ds={} speed={} target={} hold={} servo={} moved={} {}",
                shortId(train.id()), train.level().getGameTime(), jump ? "JUMP" : "step", String.format("%.4f", ds),
                String.format("%.4f", train.speed()), String.format("%.4f", train.targetSpeed()), train.scheduleHold(),
                train.servoBrake(), String.format("%.4f", moved), rail.moveLine());
    }

    public static void forget(UUID id) {
        LAST_CENTER.remove(id);
    }
}
