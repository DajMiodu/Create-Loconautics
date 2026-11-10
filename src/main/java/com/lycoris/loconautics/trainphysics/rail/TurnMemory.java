package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TurnMemory {

    public record BogeyId(UUID car, @Nullable BlockPos bogey) {
    }

    private record Junction(TrackNodeLocation from, TrackNodeLocation at, boolean forward) {
    }

    private static final class Pick {
        final TrackNodeLocation exit;
        final Set<BogeyId> crossed = new HashSet<>();
        long stamp;

        Pick(TrackNodeLocation exit, long stamp) {
            this.exit = exit;
            this.stamp = stamp;
        }
    }

    private static final long LIFETIME_MILLIS = 150_000L;

    private final Map<Junction, Pick> picks = new HashMap<>();
    private int total = 1;
    private long planTick = Long.MIN_VALUE;
    private Vec3 intent;
    private List<Couple<TrackNode>> route;

    public boolean planStale(long tick) {
        return planTick != tick;
    }

    public void setPlan(long tick, int total, @Nullable Vec3 intent, @Nullable List<Couple<TrackNode>> route) {
        this.planTick = tick;
        this.total = Math.max(1, total);
        this.intent = intent;
        this.route = route;
    }

    @Nullable
    public Vec3 intent() {
        return intent;
    }

    @Nullable
    public List<Couple<TrackNode>> route() {
        return route;
    }

    // a bogey that backs over a junction it already crossed is not past it any more
    public void unwind(TrackEdge leaving, boolean forward, BogeyId id) {
        if (forward || leaving == null || picks.isEmpty()) {
            return;
        }
        TrackNodeLocation at = leaving.node1.getLocation();
        TrackNodeLocation exit = leaving.node2.getLocation();
        for (Map.Entry<Junction, Pick> entry : picks.entrySet()) {
            if (entry.getKey().at().equals(at) && entry.getValue().exit.equals(exit)) {
                entry.getValue().crossed.remove(id);
            }
        }
    }

    public Map.Entry<TrackNode, TrackEdge> settle(TrackEdge leaving, boolean forward,
                                                   List<Map.Entry<TrackNode, TrackEdge>> options,
                                                   Map.Entry<TrackNode, TrackEdge> own,
                                                   @Nullable Map.Entry<TrackNode, TrackEdge> planned, BogeyId id) {
        Junction key = new Junction(leaving.node1.getLocation(), leaving.node2.getLocation(), forward);
        long now = System.currentTimeMillis();
        Pick pick = picks.get(key);
        if (pick != null && now - pick.stamp > LIFETIME_MILLIS) {
            picks.remove(key);
            pick = null;
        }
        if (pick != null && !pick.crossed.contains(id)) {
            for (Map.Entry<TrackNode, TrackEdge> option : options) {
                if (option.getKey().getLocation().equals(pick.exit)) {
                    pick.crossed.add(id);
                    pick.stamp = now;
                    finish(key, pick);
                    return option;
                }
            }
        }
        Map.Entry<TrackNode, TrackEdge> choice = planned != null ? planned : own;
        Pick fresh = new Pick(choice.getKey().getLocation(), now);
        fresh.crossed.add(id);
        picks.put(key, fresh);
        finish(key, fresh);
        return choice;
    }

    private void finish(Junction key, Pick pick) {
        if (pick.crossed.size() >= total) {
            picks.remove(key);
        }
    }
}
