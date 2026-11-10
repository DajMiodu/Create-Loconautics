package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.TurnMemory;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.graph.TrackNode;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ConsistTurns {

    private static final Map<UUID, TurnMemory> MEMORIES = new HashMap<>();

    private ConsistTurns() {
    }

    static void retain(Set<UUID> liveSubLevels) {
        MEMORIES.keySet().retainAll(liveSubLevels);
    }

    static Set<UUID> members(SableTrain train) {
        Set<UUID> ids = new HashSet<>(train.consistCache());
        ids.add(train.car().subLevelId());
        return ids;
    }

    static TurnMemory memoryFor(SableTrain train) {
        Set<UUID> ids = members(train);
        UUID key = ids.stream().min(Comparator.naturalOrder()).orElse(train.car().subLevelId());
        TurnMemory memory = MEMORIES.computeIfAbsent(key, id -> new TurnMemory());
        long tick = train.level().getGameTime();
        if (memory.planStale(tick)) {
            int bogeys = 0;
            for (UUID id : ids) {
                SableTrain member = SableTrainRegistry.bySubLevel(id);
                if (member != null && member.car() != null) {
                    bogeys += member.car().bogeys().size() + 1;
                }
            }
            SableTrain driver = governing(train);
            Direction intent = driver == null ? null : driver.steerIntent();
            memory.setPlan(tick, bogeys, intent == null ? null : new Vec3(intent.getStepX(), 0.0, intent.getStepZ()),
                    driver == null ? null : routeOf(driver));
        }
        return memory;
    }

    // several drivers at once, so every car must pick the same one
    @Nullable
    static SableTrain governing(SableTrain train) {
        List<SableTrain> drivers = new ArrayList<>();
        for (UUID id : members(train)) {
            SableTrain member = SableTrainRegistry.bySubLevel(id);
            if (member != null && member.car() != null && member.car().subLevelId() != null && isDriver(member)) {
                drivers.add(member);
            }
        }
        if (drivers.isEmpty()) {
            return null;
        }
        drivers.sort(Comparator.comparing((SableTrain d) -> steerInput(d) == 0)
                .thenComparing(d -> routeOf(d) == null)
                .thenComparing(d -> d.car().subLevelId()));
        return drivers.get(0);
    }

    static boolean isDriver(SableTrain train) {
        return train.isGhost() ? !train.isDerailed() && !train.isPhysics() && train.isScheduleDriven()
                : TrainSteering.isDrivenLeader(train);
    }

    private static int steerInput(SableTrain train) {
        if (train.isGhost()) {
            return 0;
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
        var axle = sub == null ? null : TrainPropulsion.bearingAxleIn(sub);
        return axle == null ? 0 : axle.getSteerInput();
    }

    @Nullable
    private static List<Couple<TrackNode>> routeOf(SableTrain train) {
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            List<Couple<TrackNode>> path = bogey.rail() == null ? null : bogey.rail().remainingPath();
            if (path != null && !path.isEmpty()) {
                return path;
            }
        }
        RailCarriage span = train.car().carriage();
        List<Couple<TrackNode>> path = span == null ? null : span.remainingPath();
        return path == null || path.isEmpty() ? null : path;
    }
}
