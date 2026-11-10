package com.lycoris.loconautics.compat.railways;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Railways is read by reflection, so it is not needed to build, and everything here is a no-op without it
public final class RailwaysSwitches {

    private RailwaysSwitches() {
    }

    private static Boolean loaded;

    public static void beginTravel(TravellingPoint front, TravellingPoint rear) {
        if (railwaysLoaded()) {
            Flag.set(true, Flag.onClosedLeg(front) || Flag.onClosedLeg(rear));
        }
    }

    public static void endTravel() {
        if (railwaysLoaded()) {
            Flag.set(false, false);
        }
    }

    private static boolean railwaysLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("railways");
        }
        return loaded;
    }

    // railways opens the unselected leg only while this is set, carriage.travel sets it, and our followers never call that
    private static final class Flag {

        private static final Field TRAVELLING;
        private static final Field SKIP_SWITCHES;
        private static final Method IS_DISABLED;

        static {
            Field travelling = null;
            Field skip = null;
            Method disabled = null;
            try {
                Class<?> variables = Class.forName("com.railwayteam.railways.util.MixinVariables");
                travelling = variables.getField("trackEdgeCarriageTravelling");
                skip = variables.getField("temporarilySkipSwitches");
                disabled = Class.forName("com.railwayteam.railways.mixin_interfaces.ISwitchDisabledEdge")
                        .getMethod("isDisabled", TrackEdge.class);
            } catch (Throwable missing) {
                travelling = null;
                skip = null;
                disabled = null;
            }
            TRAVELLING = travelling;
            SKIP_SWITCHES = skip;
            IS_DISABLED = disabled;
        }

        static void set(boolean travelling, boolean skipSwitches) {
            if (TRAVELLING == null || SKIP_SWITCHES == null) {
                return;
            }
            try {
                TRAVELLING.setBoolean(null, travelling);
                SKIP_SWITCHES.setBoolean(null, skipSwitches);
            } catch (Throwable failed) {
                // leaves the flags as railways had them
            }
        }

        // our bogeys move alone, so one can sit on the closed leg while the other holds the open one, and railways blocks the way back
        static boolean onClosedLeg(TravellingPoint point) {
            if (IS_DISABLED == null || point.edge == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(IS_DISABLED.invoke(null, point.edge));
            } catch (Throwable failed) {
                return false;
            }
        }
    }
}
