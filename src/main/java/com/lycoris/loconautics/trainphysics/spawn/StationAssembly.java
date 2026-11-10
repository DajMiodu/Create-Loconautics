package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.mixin.sable.station.StationBlockEntityAccessor;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.station.StationLastStyle;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class StationAssembly {

    public static void assembleFromStation(ServerPlayer player, BlockPos stationPos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(stationPos) instanceof StationBlockEntity station)) {
            return;
        }
        StationBlockEntityAccessor acc = (StationBlockEntityAccessor) station;

        if (!LoconauticsConfig.ENABLE_SABLE_TRAINS.get()) {
            fail(acc, "disabled", -1);
            return;
        }

        station.refreshAssemblyInfo();
        Direction dir = station.getAssemblyDirection();
        if (dir == null) {
            fail(acc, "no_track", -1);
            return;
        }
        BoundingBox area = StationBlockEntity.assemblyAreas.get(level).get(stationPos);
        if (area == null || acc.loconautics$getBogeyCount() == 0) {
            fail(acc, "no_bogeys", -1);
            return;
        }

        Vec3 railDir = Vec3.atLowerCornerOf(dir.getNormal());
        BlockPos stationTrack = station.edgePoint.getGlobalPosition();
        int bogeyY = stationTrack.getY() + 1;

        int width = LoconauticsConfig.STATION_SCAN_WIDTH.get();
        int minY = bogeyY - LoconauticsConfig.STATION_SCAN_DEPTH.get();
        int maxY = bogeyY + LoconauticsConfig.STATION_SCAN_HEIGHT.get();
        AABB scan = new AABB(
                area.minX() - width, minY, area.minZ() - width,
                area.maxX() + 1 + width, maxY + 1, area.maxZ() + 1 + width);

        List<BlockPos> bogeys = bogeysOnAssemblyLine(level, area, bogeyY);
        if (bogeys.isEmpty()) {
            fail(acc, "no_bogeys", -1);
            return;
        }
        bogeys.sort(Comparator.comparingDouble(
                b -> (b.getX() - stationTrack.getX()) * railDir.x + (b.getZ() - stationTrack.getZ()) * railDir.z));
        BlockPos frontBogey = bogeys.get(0);

        List<AABB> honeyGlue = collectHoneyGlue(level, scan);
        Set<BlockPos> claimed = new HashSet<>();
        List<Set<BlockPos>> carriages = new ArrayList<>();
        for (BlockPos bogeyPos : bogeys) {
            if (claimed.contains(bogeyPos)) {
                continue;
            }
            Set<BlockPos> cluster = floodFillCarriage(level, bogeyPos, scan, claimed, honeyGlue);
            claimed.addAll(cluster);
            carriages.add(cluster);
        }
        ArticulationLinks.absorbBogeylessFronts(level, carriages, claimed, scan, honeyGlue);

        Set<BlockPos> frontCarriage = carriageContaining(carriages, frontBogey);
        if (frontCarriage == null) {
            failNothingAttached(acc, 1);
            return;
        }

        carriages.sort(Comparator.comparingDouble(c -> projectionAlong(c, stationTrack, railDir)));

        int maxBogeys = LoconauticsConfig.MAX_BOGEYS_PER_CARRIAGE.get();
        for (int i = 0; i < carriages.size(); i++) {
            if (countBogeysIn(level, carriages.get(i)) > maxBogeys) {
                acc.loconautics$exception(new AssemblyException(
                        Component.translatable("loconautics.station.sabletrain.too_many_bogeys", maxBogeys)), i + 1);
                return;
            }
        }

        for (int i = 0; i < carriages.size(); i++) {
            Set<BlockPos> cluster = carriages.get(i);
            if (!SableTrainSpawner.hasRail(level, cluster, boundsOf(cluster), railDir,
                    frontmostBlock(cluster, stationTrack, railDir))) {
                acc.loconautics$exception(new AssemblyException(
                        Component.translatable("loconautics.station.sabletrain.no_rail")), i + 1);
                return;
            }
        }

        List<ArticulationLinks.Hinge> hinges = ArticulationLinks.collect(level, carriages);
        Map<Set<BlockPos>, ArticulationLinks.BuiltCar> builtCars = new IdentityHashMap<>();
        Set<BlockPos> assembled = new HashSet<>();
        String baseName = station.lastDisassembledTrainName != null
                ? station.lastDisassembledTrainName.getString()
                : SableTrainSpawner.defaultTrainName();
        station.lastDisassembledTrainName = null;
        int mapColor = station.lastDisassembledMapColorIndex;
        String icon = ((StationLastStyle) station).loconautics$getLastIcon();
        station.lastDisassembledMapColorIndex = 0;
        ((StationLastStyle) station).loconautics$setLastIcon("");
        int built = 0;
        boolean blockBreakers = false;
        GlobalStation parkedAt = station.getStation();
        for (Set<BlockPos> cluster : carriages) {
            BoundingBox3i bounds = boundsOf(cluster);
            BlockPos anchor = frontmostBlock(cluster, stationTrack, railDir);
            blockBreakers |= containsBlockBreakers(level, cluster);
            SableTrain.Car car = SableTrainSpawner.seatCar(level, cluster, bounds, railDir, anchor);
            if (car == null) {
                LoconauticsConstants.LOGGER.warn(
                        "[sabletrain] station assembly: a carriage could not be seated (no rail/graph?), skipped");
                continue;
            }
            UUID id = UUID.randomUUID();
            SableTrain train = new SableTrain(id, level, car, false);
            train.setOwner(player == null ? null : player.getUUID());
            train.setTargetSpeed(0.0);
            train.setMapColorIndex(mapColor);
            train.setIconType(icon);
            train.setAtStation(parkedAt);
            train.setCurrentStation(parkedAt);
            train.setStationState(SableTrain.StationState.STOPPED);
            SableTrainRegistry.register(train);
            SableTrainSpawner.nameCar(level, car, baseName);
            SubLevelSplitWatcher.ensureObserver(level);
            SableTrainSpawner.lastTrainId = id;
            SableTrainPersistence.persist(train);
            SableTrainSyncPacket.broadcast(train);
            assembled.addAll(cluster);
            builtCars.put(cluster, new ArticulationLinks.BuiltCar(car, anchor));
            built++;
        }
        if (built == 0) {
            fail(acc, "no_bogeys", -1);
            return;
        }
        ArticulationLinks.linkAll(level, hinges, builtCars);

        acc.loconautics$exception(null, -1);
        station.exitAssemblyMode();
        TrainAdvancements.assembled(player, built, blockBreakers);
        LoconauticsConstants.LOGGER.info("[sabletrain] station at {} assembled {} independent Sable car(s) (idle)",
                stationPos, built);
    }

    // read before assembly, the blocks leave the world afterwards
    private static boolean containsBlockBreakers(ServerLevel level, Set<BlockPos> cluster) {
        for (BlockPos pos : cluster) {
            MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(level.getBlockState(pos));
            if (behaviour instanceof BlockBreakingMovementBehaviour || behaviour instanceof HarvesterMovementBehaviour) {
                return true;
            }
        }
        return false;
    }

    static void fail(StationBlockEntityAccessor acc, String key, int carriage) {
        acc.loconautics$exception(
                new AssemblyException(Component.translatable("loconautics.station.sabletrain." + key)), carriage);
    }

    static void failNothingAttached(StationBlockEntityAccessor acc, int bogeyNumber) {
        acc.loconautics$exception(
                new AssemblyException(CreateLang.translateDirect("train_assembly.nothing_attached", bogeyNumber)), -1);
    }

    static Set<BlockPos> carriageContaining(List<Set<BlockPos>> carriages, BlockPos pos) {
        for (Set<BlockPos> carriage : carriages) {
            if (carriage.contains(pos)) {
                return carriage;
            }
        }
        return null;
    }

    static List<BlockPos> bogeysOnAssemblyLine(ServerLevel level, BoundingBox area, int bogeyY) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                if (level.getBlockState(pos.set(x, bogeyY, z)).getBlock() instanceof AbstractBogeyBlock) {
                    out.add(pos.immutable());
                } else if (level.getBlockState(pos.set(x, bogeyY - 2, z)).getBlock() instanceof AbstractBogeyBlock) {
                    out.add(pos.immutable());
                }
            }
        }
        return out;
    }

    static List<AABB> collectHoneyGlue(ServerLevel level, AABB scan) {
        try {
            return honeyGlueBoxes(level, scan);
        } catch (NoClassDefFoundError ignored) {
            return List.of();
        }
    }

    private static List<AABB> honeyGlueBoxes(ServerLevel level, AABB scan) {
        List<AABB> out = new ArrayList<>();
        for (HoneyGlueEntity g : level.getEntitiesOfClass(HoneyGlueEntity.class, scan)) {
            out.add(g.getBoundingBox());
        }
        return out;
    }

    static boolean honeyGlued(List<AABB> honeyGlue, BlockPos pos, BlockPos next) {
        Vec3 a = Vec3.atCenterOf(pos);
        Vec3 b = Vec3.atCenterOf(next);
        for (AABB box : honeyGlue) {
            if (box.contains(a) && box.contains(b)) {
                return true;
            }
        }
        return false;
    }

    static Set<BlockPos> floodFillCarriage(ServerLevel level, BlockPos start, AABB scan, Set<BlockPos> claimed,
                                           List<AABB> honeyGlue) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(start);
        frontier.add(start);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            BlockState state = level.getBlockState(pos);
            for (Direction offset : Direction.values()) {
                BlockPos next = pos.relative(offset);
                if (visited.contains(next) || claimed.contains(next) || !scan.contains(Vec3.atCenterOf(next))) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir() || nextState.getBlock() instanceof ITrackBlock
                        || !BlockMovementChecks.isMovementNecessary(nextState, level, next)) {
                    continue;
                }
                if (ArticulationLinks.isHinge(state, nextState, offset)) {
                    continue;
                }
                if (isStructurallyAttached(level, pos, state, next, nextState, offset, honeyGlue)) {
                    visited.add(next);
                    frontier.add(next);
                }
            }
        }
        return visited;
    }

    static boolean isStructurallyAttached(ServerLevel level, BlockPos pos, BlockState state,
                                           BlockPos next, BlockState nextState, Direction offset,
                                           List<AABB> honeyGlue) {
        if (honeyGlued(honeyGlue, pos, next)) {
            return true;
        }
        if (state.getBlock() instanceof AbstractBogeyBlock<?> bogey
                && bogey.getStickySurfaces(level, pos, state).contains(offset)) {
            return true;
        }
        if (SuperGlueEntity.isGlued(level, pos, offset, null)) {
            return true;
        }
        if (BlockMovementChecks.isBlockAttachedTowards(nextState, level, next, offset.getOpposite())) {
            return true;
        }
        if (BlockMovementChecks.isBrittle(nextState)) {
            return false;
        }
        if (!state.canStickTo(nextState) || !nextState.canStickTo(state)) {
            return false;
        }
        if (state.getPistonPushReaction() == PushReaction.PUSH_ONLY
                || nextState.getPistonPushReaction() == PushReaction.PUSH_ONLY) {
            return false;
        }
        return !BlockMovementChecks.isNotSupportive(state, offset)
                && !BlockMovementChecks.isNotSupportive(nextState, offset.getOpposite());
    }

    static void moveGlueIntoSubLevel(ServerLevel level, Set<BlockPos> blocks, BoundingBox3i bounds,
                                             ServerSubLevel sub, BlockPos anchor) {
        Vec3 shift = Vec3.atLowerCornerOf(sub.getPlot().getCenterBlock().subtract(anchor));
        AABB scan = bounds.toAABB().inflate(1.0);
        for (SuperGlueEntity g : level.getEntitiesOfClass(SuperGlueEntity.class, scan)) {
            if (assembledBlocksInside(g.getBoundingBox(), blocks) >= 2) {
                AABB box = g.getBoundingBox().move(shift);
                g.remove(Entity.RemovalReason.KILLED);
                level.addFreshEntity(new SuperGlueEntity(level, box));
            }
        }
        try {
            moveHoneyGlueIntoSubLevel(level, blocks, scan, shift);
        } catch (NoClassDefFoundError ignored) {
        }
    }

    static void moveHoneyGlueIntoSubLevel(ServerLevel level, Set<BlockPos> blocks, AABB scan, Vec3 shift) {
        for (HoneyGlueEntity g : level.getEntitiesOfClass(HoneyGlueEntity.class, scan)) {
            if (assembledBlocksInside(g.getBoundingBox(), blocks) >= 2) {
                AABB box = g.getBoundingBox().move(shift);
                g.remove(Entity.RemovalReason.KILLED);
                HoneyGlueEntity moved = new HoneyGlueEntity(level, box);
                level.addFreshEntity(moved);
                moved.setBoundsAndSync(box);
            }
        }
    }

    static int assembledBlocksInside(AABB box, Set<BlockPos> blocks) {
        int n = 0;
        for (BlockPos p : blocks) {
            if (box.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) && ++n >= 2) {
                return n;
            }
        }
        return n;
    }

    static int countMatching(ServerLevel level, Set<BlockPos> cluster, Predicate<Block> test) {
        int count = 0;
        for (BlockPos p : cluster) {
            if (test.test(level.getBlockState(p).getBlock())) {
                count++;
            }
        }
        return count;
    }

    static int countBogeysIn(ServerLevel level, Set<BlockPos> cluster) {
        return countMatching(level, cluster, b -> b instanceof AbstractBogeyBlock);
    }

    static double projectionAlong(Set<BlockPos> cluster, BlockPos station, Vec3 railDir) {
        double sum = 0.0;
        for (BlockPos p : cluster) {
            sum += (p.getX() - station.getX()) * railDir.x + (p.getZ() - station.getZ()) * railDir.z;
        }
        return sum / cluster.size();
    }

    static BlockPos frontmostBlock(Set<BlockPos> cluster, BlockPos station, Vec3 railDir) {
        return cluster.stream()
                .min(Comparator.comparingDouble(
                        p -> (p.getX() - station.getX()) * railDir.x + (p.getZ() - station.getZ()) * railDir.z))
                .orElseGet(() -> cluster.iterator().next());
    }

    static BoundingBox3i boundsOf(Set<BlockPos> cluster) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : cluster) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        return new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private StationAssembly() {
    }
}
