package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

// temporary, tag schedghost, remove once lyc confirms a parked unloaded train departs
final class ScheduleGhostTrace {

    private static final long INTERVAL_TICKS = 20L;

    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private ScheduleGhostTrace() {
    }

    static boolean due(SableTrain train) {
        if (train.level() == null) {
            return false;
        }
        long now = train.level().getGameTime();
        Long last = LAST.get(train.id());
        if (last != null && now - last < INTERVAL_TICKS) {
            return false;
        }
        LAST.put(train.id(), now);
        return true;
    }

    static void log(SableTrain train, String text) {
        LoconauticsConstants.LOGGER.info("[schedghost] {} ghost={} residency={} {}", shortId(train.id()),
                train.isGhost(), train.residency(), text);
    }

    static void event(@Nullable SableTrain train, String text) {
        if (train == null) {
            LoconauticsConstants.LOGGER.info("[schedghost] event {}", text);
            return;
        }
        log(train, "event " + text);
    }
}
