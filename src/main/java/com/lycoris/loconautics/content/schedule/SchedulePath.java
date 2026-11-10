package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record SchedulePath(TrackGraphLocation target, int direction,
                           @Nullable List<Couple<TrackNode>> route, @Nullable GlobalStation station,
                           @Nullable TurntableRegistry.Exit hop) {

    public SchedulePath(TrackGraphLocation target, int direction,
                        @Nullable List<Couple<TrackNode>> route, @Nullable GlobalStation station) {
        this(target, direction, route, station, null);
    }

    public boolean followsRoute() {
        return route != null;
    }
}
