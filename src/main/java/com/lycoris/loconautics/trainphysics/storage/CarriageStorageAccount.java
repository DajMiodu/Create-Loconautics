package com.lycoris.loconautics.trainphysics.storage;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CarriageStorageAccount {

    private CarriageStorageAccount() {
    }

    public static List<IItemHandler> itemsFor(ServerLevel level, UUID headId, int carriageIndex) {
        List<IItemHandler> out = new ArrayList<>();
        Set<IItemHandler> seen = identitySet();
        for (ServerSubLevel sub : reachableSubLevels(level, headId, carriageIndex)) {
            for (IItemHandler handler : CarriageStorage.items(sub)) {
                if (seen.add(handler)) {
                    out.add(handler);
                }
            }
        }
        return out;
    }

    public static List<IFluidHandler> fluidsFor(ServerLevel level, UUID headId, int carriageIndex) {
        List<IFluidHandler> out = new ArrayList<>();
        Set<IFluidHandler> seen = identitySet();
        for (ServerSubLevel sub : reachableSubLevels(level, headId, carriageIndex)) {
            for (IFluidHandler handler : CarriageStorage.fluids(sub)) {
                if (seen.add(handler)) {
                    out.add(handler);
                }
            }
        }
        return out;
    }

    private static List<ServerSubLevel> reachableSubLevels(ServerLevel level, UUID headId, int carriageIndex) {
        List<ServerSubLevel> out = new ArrayList<>();
        for (UUID id : reachableSubLevelIds(level, headId, carriageIndex)) {
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            if (sub != null) {
                out.add(sub);
            }
        }
        return out;
    }

    // vault chained off a vault still counts. the one rule doing all the work never step from a chain member into another chain member unless that member is already one of our anchors, otherwise
    private static Set<UUID> reachableSubLevelIds(ServerLevel level, UUID headId, int carriageIndex) {
        List<UUID> chain = TrainConsist.orderedChain(level, headId);
        Set<UUID> chainSet = new HashSet<>(chain);

        Set<UUID> anchors;
        if (carriageIndex == ConsistAccess.WHOLE_CONSIST) {
            anchors = chainSet;
        } else if (carriageIndex >= 0 && carriageIndex < chain.size()) {
            anchors = Set.of(chain.get(carriageIndex));
        } else {
            return Set.of();
        }

        Set<UUID> visited = new HashSet<>(anchors);
        Deque<UUID> queue = new ArrayDeque<>(anchors);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (UUID neighbour : TrainConsist.neighbours(level, current)) {
                if (chainSet.contains(neighbour) && !anchors.contains(neighbour)) {
                    continue;
                }
                if (visited.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return visited;
    }

    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
