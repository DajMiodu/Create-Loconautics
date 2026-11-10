package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.trainphysics.SableTrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SableTrainRegistry {

    private static final Map<UUID, SableTrain> TRAINS = new ConcurrentHashMap<>();

    private SableTrainRegistry() {
    }

    public static void register(SableTrain train) {
        TRAINS.put(train.id(), train);
    }

    public static SableTrain get(UUID id) {
        return TRAINS.get(id);
    }

    public static SableTrain remove(UUID id) {
        return TRAINS.remove(id);
    }

    public static void clear() {
        TRAINS.clear();
    }

    public static boolean isEmpty() {
        return TRAINS.isEmpty();
    }

    public static Collection<SableTrain> all() {
        return new ArrayList<>(TRAINS.values());
    }

    public static SableTrain bySubLevel(UUID subLevelId) {
        if (subLevelId == null) {
            return null;
        }
        for (SableTrain train : TRAINS.values()) {
            if (subLevelId.equals(train.car().subLevelId())) {
                return train;
            }
        }
        return null;
    }
}
