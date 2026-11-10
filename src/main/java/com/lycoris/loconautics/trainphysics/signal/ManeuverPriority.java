package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class ManeuverPriority {

    private static final double PARALLEL = 1.0e-3;

    static boolean isManeuvering(UUID trainId) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(trainId);
        return runtime != null && runtime.maneuver();
    }

    static boolean mustWait(SableTrain self, SableTrain other, boolean otherHolds, boolean selfHolds, TrackNode junction) {
        if (otherHolds != selfHolds) {
            return otherHolds;
        }
        double cross = approachCross(self, other, junction);
        if (Math.abs(cross) > PARALLEL) {
            boolean wait = cross < 0.0;
            return wait;
        }
        boolean wait = self.id().compareTo(other.id()) > 0;
        return wait;
    }

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

    private ManeuverPriority() {
    }
}
