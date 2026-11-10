package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class ManeuverPriority {

    private static final double PARALLEL = 1.0e-3;

    // one log line per change, the scan runs every tick
    private static final java.util.Map<String, String> LAST = new java.util.HashMap<>();

    static boolean isManeuvering(UUID trainId) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(trainId);
        return runtime != null && runtime.maneuver();
    }

    // true when self has to wait for other, both maneuvering and both wanting the block behind junction
    static boolean mustWait(SableTrain self, SableTrain other, boolean otherHolds, boolean selfHolds, TrackNode junction) {
        if (otherHolds != selfHolds) {
            log(self, other, "reservation", otherHolds);
            return otherHolds;
        }
        double cross = approachCross(self, other, junction);
        if (Math.abs(cross) > PARALLEL) {
            boolean wait = cross < 0.0;
            log(self, other, "right of way", wait);
            return wait;
        }
        boolean wait = self.id().compareTo(other.id()) > 0;
        log(self, other, "lower uuid", wait);
        return wait;
    }

    // positive when self comes in on the right of others heading, nan data counts as zero
    private static double approachCross(SableTrain self, SableTrain other, TrackNode junction) {
        Vec3 a = approach(self, junction);
        Vec3 b = approach(other, junction);
        if (a == null || b == null) {
            return 0.0;
        }
        return a.x * b.z - a.z * b.x;
    }

    private static Vec3 approach(SableTrain train, TrackNode junction) {
        RailCarriage rail = train.car() == null ? null : train.car().carriage();
        TrackGraph graph = rail == null ? null : rail.graph();
        TravellingPoint nose = TrainStations.nosePoint(train);
        if (graph == null || nose == null || nose.edge == null) {
            return null;
        }
        Vec3 from = rail.worldPointOf(nose);
        Vec3 to = junction.getLocation().getLocation();
        Vec3 flat = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        double length = flat.length();
        return length < 1.0e-6 ? null : flat.scale(1.0 / length);
    }

    private static void log(SableTrain self, SableTrain other, String rule, boolean wait) {
        String line = rule + wait;
        if (line.equals(LAST.put(self.id().toString() + other.id(), line))) {
            return;
        }
        LoconauticsConstants.LOGGER.info("[maneuver] priority {} vs {} decided by {}, {} waits",
                ConsistNose.shortId(self.id()), ConsistNose.shortId(other.id()), rule,
                wait ? ConsistNose.shortId(self.id()) : ConsistNose.shortId(other.id()));
    }

    private ManeuverPriority() {
    }
}
