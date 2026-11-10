package com.lycoris.loconautics.compat.crn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

public final class CrnTrainEvents {

    private record Hooks(Class<?> event, Method getEvent, Method run) {}

    private static final Hooks HOOKS = resolve();

    private CrnTrainEvents() {
    }

    private static Hooks resolve() {
        if (!ModList.get().isLoaded("createrailwaysnavigator")) {
            return null;
        }
        try {
            Class<?> event = Class.forName("de.mrjulsen.crn.event.events.TrainArrivalAndDepartureEvent");
            Method getEvent = Class.forName("de.mrjulsen.crn.event.CRNEventsManager")
                    .getMethod("getEventOptional", Class.class);
            return new Hooks(event, getEvent, event.getMethod("run", Train.class, Optional.class, boolean.class));
        } catch (ReflectiveOperationException e) {
            LoconauticsConstants.LOGGER.debug("Railways Navigator arrival event lookup failed", e);
            return null;
        }
    }

    public static void arrived(Train shadow, GlobalStation station) {
        fire(shadow, station, true);
    }

    public static void departed(Train shadow, GlobalStation station) {
        fire(shadow, station, false);
    }

    private static void fire(Train shadow, GlobalStation station, boolean arrival) {
        if (HOOKS == null) {
            return;
        }
        try {
            Object event = ((Optional<?>) HOOKS.getEvent().invoke(null, HOOKS.event())).orElse(null);
            if (event != null) {
                HOOKS.run().invoke(event, shadow, Optional.of(station), arrival);
            }
        } catch (ReflectiveOperationException e) {
            LoconauticsConstants.LOGGER.debug("Railways Navigator arrival event failed", e);
        }
    }
}
