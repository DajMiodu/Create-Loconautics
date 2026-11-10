package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.trainphysics.rail.TrackAhead;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class CcTrackAhead {

    private CcTrackAhead() {
    }

    static double range(IArguments args, int index) throws LuaException {
        double range = args.optDouble(index).orElse(TrackAhead.DEFAULT_RANGE);
        if (range < 1.0 || range > TrackAhead.MAX_RANGE) {
            throw new LuaException("range must be between 1 and " + (int) TrackAhead.MAX_RANGE + " blocks");
        }
        return range;
    }

    static String typeOf(UUID host) {
        return host == null ? "world" : "sublevel";
    }

    static Map<String, Object> end(TrackAhead.Report report) {
        TrackAhead.End end = report == null ? null : report.end();
        if (end == null) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("distance", end.distance());
        position(entry, end.world());
        entry.put("type", typeOf(end.host()));
        host(entry, end.host());
        return entry;
    }

    static Map<String, Object> curve(TrackAhead.Curve curve) {
        if (curve == null) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("distance", curve.distance());
        entry.put("length", curve.length());
        entry.put("angle", curve.degrees());
        entry.put("direction", curve.degrees() < 0.0 ? "left" : "right");
        entry.put("radius", curve.radius());
        entry.put("type", typeOf(curve.host()));
        return entry;
    }

    static Map<String, Object> slopeChange(TrackAhead.SlopeChange change) {
        if (change == null) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("distance", change.distance());
        entry.put("from", change.from());
        entry.put("to", change.to());
        return entry;
    }

    static Map<String, Object> change(TrackAhead.TrackChange change) {
        if (change == null) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("distance", change.distance());
        entry.put("from", typeOf(change.from()));
        entry.put("to", typeOf(change.to()));
        entry.put("joined", change.joined());
        entry.put("gap", change.gap());
        position(entry, change.world());
        host(entry, change.to());
        return entry;
    }

    static Double distance(Map<String, Object> entry) {
        return entry == null ? null : (Double) entry.get("distance");
    }

    private static void position(Map<String, Object> entry, Vec3 world) {
        if (world != null) {
            entry.put("x", world.x);
            entry.put("y", world.y);
            entry.put("z", world.z);
        }
    }

    private static void host(Map<String, Object> entry, UUID host) {
        if (host != null) {
            entry.put("subLevelId", host.toString());
        }
    }
}
