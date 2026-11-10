package com.lycoris.loconautics.compat.crn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CrnTrainEvents {

    private record Hooks(Class<?> event, Method getEvent, Method run) {}

    private record Probe(Method trainData, Method initialized, Method preInit, Method predictions) {}

    private static final Hooks HOOKS = resolve();

    private static final Probe PROBE = resolveProbe();

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

    private static Probe resolveProbe() {
        if (HOOKS == null) {
            return null;
        }
        try {
            Class<?> data = Class.forName("de.mrjulsen.crn.data.train.TrainData");
            return new Probe(
                    Class.forName("de.mrjulsen.crn.data.train.TrainListener").getMethod("getTrainData", UUID.class),
                    data.getMethod("isInitialized"),
                    data.getMethod("isPreInitializationPhase"),
                    data.getMethod("getPredictions"));
        } catch (ReflectiveOperationException e) {
            LoconauticsConstants.LOGGER.debug("Railways Navigator train data lookup failed", e);
            return null;
        }
    }

    public static String describe(UUID trainId) {
        if (PROBE == null) {
            return "no navigator";
        }
        try {
            Object data = ((Optional<?>) PROBE.trainData().invoke(null, trainId)).orElse(null);
            if (data == null) {
                return "no train data";
            }
            return "initialized=" + PROBE.initialized().invoke(data)
                    + " preInit=" + PROBE.preInit().invoke(data)
                    + " predictions=" + ((List<?>) PROBE.predictions().invoke(data)).size();
        } catch (ReflectiveOperationException e) {
            return "probe failed: " + e.getMessage();
        }
    }

    public static void arrived(Train shadow, GlobalStation station) {
        fire(shadow, station, true);
    }

    public static void departed(Train shadow, GlobalStation station) {
        fire(shadow, station, false);
    }

    // we never call train.arriveat, so the navigator mixin on it stays quiet and its history stays empty
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
