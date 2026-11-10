package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NozzleDockService {

    private static final Set<HoseNozzleBlockEntity> NOZZLES = ConcurrentHashMap.newKeySet();

    private NozzleDockService() {
    }

    public static void register(HoseNozzleBlockEntity nozzle) {
        NOZZLES.add(nozzle);
    }

    public static void unregister(HoseNozzleBlockEntity nozzle) {
        NOZZLES.remove(nozzle);
    }

    public static Set<HoseNozzleBlockEntity> all() {
        return NOZZLES;
    }
}
