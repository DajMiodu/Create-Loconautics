package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.trainphysics.SableTrain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class CcEvents {

    public interface Target {
        UUID watchedTrain();

        void queue(String event, Object[] arguments);

        default boolean watches(UUID trainId) {
            return trainId.equals(watchedTrain());
        }

        default boolean watchesNetwork() {
            return false;
        }
    }

    private static final Set<Target> TARGETS = new LinkedHashSet<>();

    private CcEvents() {
    }

    public static void register(Target target) {
        TARGETS.add(target);
    }

    public static void unregister(Target target) {
        TARGETS.remove(target);
    }

    public static void queue(SableTrain train, String event, Object... extra) {
        if (train != null) {
            queue(train.id(), event, extra);
        }
    }

    public static void broadcast(String event, Object... arguments) {
        if (TARGETS.isEmpty()) {
            return;
        }
        for (Target target : new ArrayList<>(TARGETS)) {
            if (target.watchesNetwork()) {
                target.queue(event, arguments);
            }
        }
    }

    public static boolean anyWatchesNetwork() {
        for (Target target : TARGETS) {
            if (target.watchesNetwork()) {
                return true;
            }
        }
        return false;
    }

    public static void queue(UUID id, String event, Object... extra) {
        if (TARGETS.isEmpty() || id == null) {
            return;
        }
        Object[] arguments = new Object[extra.length + 1];
        arguments[0] = id.toString();
        System.arraycopy(extra, 0, arguments, 1, extra.length);
        for (Target target : new ArrayList<>(TARGETS)) {
            if (target.watches(id) || target.watchesNetwork()) {
                target.queue(event, arguments);
            }
        }
    }
}
