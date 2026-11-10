package com.lycoris.loconautics.trainphysics;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.coupling.CouplingProvider;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class TrainConsist {

    private static final List<CouplingProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private TrainConsist() {
    }

    public static void registerProvider(CouplingProvider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    public static Set<UUID> neighbours(ServerLevel level, UUID id) {
        Set<UUID> out = new HashSet<>();
        if (id == null) {
            return out;
        }
        for (CouplingProvider provider : PROVIDERS) {
            provider.collectCoupledSubLevels(level, id, neighbour -> {
                if (neighbour != null && !neighbour.equals(id)) {
                    out.add(neighbour);
                }
            });
        }
        return out;
    }

    public static List<UUID> orderedConsist(ServerLevel level, UUID start) {
        List<UUID> order = new java.util.ArrayList<>();
        if (start == null) {
            return order;
        }
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            order.add(current);
            for (UUID n : neighbours(level, current)) {
                if (visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        return order;
    }

    public static Set<UUID> weldedSubLevels(ServerLevel level, UUID start) {
        Set<UUID> welded = new HashSet<>();
        if (start == null) {
            return welded;
        }
        Set<UUID> visited = new HashSet<>();
        visited.add(start);
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (CouplingProvider provider : PROVIDERS) {
                provider.collectWeldedSubLevels(level, current, neighbour -> {
                    if (neighbour != null && visited.add(neighbour)) {
                        welded.add(neighbour);
                        queue.add(neighbour);
                    }
                });
            }
        }
        return welded;
    }

    public static List<UUID> orderedChain(ServerLevel level, UUID start) {
        List<UUID> arm = walkArm(level, start);
        if (arm.size() < 2) {
            return arm;
        }
        return walkArm(level, arm.get(arm.size() - 1));
    }

    private static List<UUID> walkArm(ServerLevel level, UUID start) {
        List<UUID> chain = new java.util.ArrayList<>();
        if (start == null) {
            return chain;
        }
        Set<UUID> visited = new HashSet<>();
        UUID current = start;
        while (current != null && visited.add(current)) {
            chain.add(current);
            UUID next = null;
            for (UUID n : neighbours(level, current)) {
                if (!visited.contains(n)) {
                    next = n;
                    break;
                }
            }
            current = next;
        }
        return chain;
    }

    @Nullable
    public static ServerSubLevel resolveFront(ServerLevel level, UUID frontId) {
        ServerSubLevel frontSub = SubLevelData.resolve(level, frontId);
        return frontSub;
    }

    public static List<ServerSubLevel> subLevelsBehind(ServerLevel level, UUID frontId) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        ServerSubLevel frontSub = resolveFront(level, frontId);
        if (container == null || frontSub == null) {
            return List.of();
        }
        Vector3dc fp = frontSub.logicalPose().position();
        List<ServerSubLevel> behind = new ArrayList<>();
        for (UUID id : connectedSubLevels(level, frontId)) {
            if (id.equals(frontId)) {
                continue;
            }
            if (container.getSubLevel(id) instanceof ServerSubLevel s && !s.isRemoved()) {
                behind.add(s);
            }
        }
        behind.sort(Comparator.comparingDouble(s -> {
            Vector3dc p = s.logicalPose().position();
            double dx = p.x() - fp.x();
            double dz = p.z() - fp.z();
            return dx * dx + dz * dz;
        }));
        return behind;
    }

    public static Set<UUID> connectedSubLevels(ServerLevel level, UUID start) {
        Set<UUID> visited = new HashSet<>();
        if (start == null) {
            return visited;
        }
        visited.add(start);
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (CouplingProvider provider : PROVIDERS) {
                provider.collectCoupledSubLevels(level, current, neighbour -> {
                    if (neighbour != null && visited.add(neighbour)) {
                        queue.add(neighbour);
                    }
                });
            }
        }
        return visited;
    }

    public static boolean standingOnConsistOf(ServerLevel level, Entity entity, UUID body) {
        SubLevel standing = Sable.HELPER.getTrackingSubLevel(entity);
        return standing != null && (standing.getUniqueId().equals(body)
                || connectedSubLevels(level, standing.getUniqueId()).contains(body));
    }

    public static double totalMass(ServerLevel level, UUID start) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return 0.0;
        }
        double total = 0.0;
        for (UUID id : connectedSubLevels(level, start)) {
            if (container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                total += subLevelMass(sub);
            }
        }
        return total;
    }

    public static double subLevelMass(ServerSubLevel sub) {
        ServerLevel subLevel = sub.getLevel();
        double[] mass = {0.0};
        SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
            mass[0] += PhysicsBlockPropertyHelper.getMass(subLevel, pos, state);
            return true;
        });
        return mass[0];
    }
}
