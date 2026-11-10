package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spacing.CarExtent;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class SableTrainPersistence {

    private static final int RESTORE_RETRY_INTERVAL = 10;

    private static final int RESTORE_LOG_INTERVAL = 100;

    private static final int RESTORE_GIVEUP_TICKS = 6000;

    // a record that throws on restore is retried each launch, this many failed launches and it is deleted
    private static final int MAX_RESTORE_FAILURES = 3;

    private static final String RESTORE_FAILURES_KEY = "RestoreFailures";

    // resnapshot live trains to disk this often, so a hard crash keeps a recent rail position.
    private static final int SNAPSHOT_INTERVAL_TICKS = 200;

    private static final Map<UUID, CompoundTag> pending = new HashMap<>();
    private static int restoreTicks;
    private static int snapshotCounter;

    private SableTrainPersistence() {
    }


    public static void persist(SableTrain train) {
        MinecraftServer server = train.level().getServer();
        if (server == null) {
            return;
        }
        try {
            SableTrainStore.get(server).put(train.id(), serialize(train));
        } catch (Throwable t) {
            LoconauticsConstants.LOGGER.error("[sabletrain] failed to persist train {}", train.id(), t);
        }
    }

    public static void unpersist(MinecraftServer server, UUID id) {
        if (server == null) {
            return;
        }
        SableTrainStore.get(server).remove(id);
        pending.remove(id);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        pending.clear();
        pending.putAll(SableTrainStore.get(server).records());
        Set<UUID> leakedShadows = new HashSet<>();
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            GhostWheelBodies.purgeSaved(level);
            leakedShadows.addAll(PortalTwins.purgeSaved(level));
        }
        dropShadowRecords(server, leakedShadows);
        restoreTicks = 0;
        snapshotCounter = 0;
        LoconauticsConstants.LOGGER.info("[sabletrain] persistence active, {} saved train(s) to restore",
                pending.size());
    }

    private static void dropShadowRecords(MinecraftServer server, Set<UUID> shadowSubLevels) {
        if (shadowSubLevels.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(pending.keySet())) {
            ListTag cars = pending.get(id).getList("Cars", Tag.TAG_COMPOUND);
            if (cars.isEmpty() || !shadowSubLevels.contains(cars.getCompound(0).getUUID("SubLevel"))) {
                continue;
            }
            unpersist(server, id);
            LoconauticsConstants.LOGGER.info("[portal] dropped saved train {}, its body was a leaked far side copy", id);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!pending.isEmpty()) {
            restoreTicks++;
            if (restoreTicks % RESTORE_RETRY_INTERVAL == 0) {
                boolean verbose = restoreTicks % RESTORE_LOG_INTERVAL == 0;
                // ahead of the drain, a car must not restore on top of last sessions loose wheel bodies
                for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                    GhostWheelBodies.purgeSaved(level);
                }
                drainPending(server, verbose);
            }
        }
        if (!SableTrainRegistry.isEmpty() && (snapshotCounter++ % SNAPSHOT_INTERVAL_TICKS) == 0) {
            snapshotAll(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        anchorCursorsToBodies();
        snapshotAll(server);
        com.lycoris.loconautics.trainphysics.portal.PortalTwins.dropAll(server);
        com.lycoris.loconautics.trainphysics.portal.PortalHold.releaseAll(server);
        StationChunkTickets.releaseAll();
        SableTrainRegistry.clear();
        com.lycoris.loconautics.trainphysics.actors.TrainActorControls.clearServer();
        pending.clear();
        restoreTicks = 0;
    }

    private static void drainPending(MinecraftServer server, boolean verbose) {
        boolean giveUp = restoreTicks >= RESTORE_GIVEUP_TICKS;
        Iterator<Map.Entry<UUID, CompoundTag>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CompoundTag> entry = it.next();
            SableTrain train;
            try {
                train = deserialize(entry.getValue(), server, verbose, entry.getKey());
            } catch (Throwable t) {
                LoconauticsConstants.LOGGER.error("[sabletrain] error restoring train {}, dropping", entry.getKey(), t);
                countFailedRestore(server, entry.getKey(), entry.getValue());
                it.remove();
                continue;
            }
            if (train != null) {
                SableTrainRegistry.register(train);
                SubLevelSplitWatcher.ensureObserver(train.level());
                SableTrainSpawner.noteRestored(train.id());                if (!train.isGhost()) {
                    TrainResidency.queueReconcile(train);
                }
                it.remove();
                LoconauticsConstants.LOGGER.info("[sabletrain] restored cart {} in {}",
                        train.id(), train.level().dimension().location());
            } else if (giveUp) {
                LoconauticsConstants.LOGGER.warn(
                        "[sabletrain] giving up on train {} this session (track graph or sub-level never became available), kept on disk for next launch",
                        entry.getKey());
                it.remove();
            }
        }
    }

    private static void countFailedRestore(MinecraftServer server, UUID id, CompoundTag record) {
        int failures = record.getInt(RESTORE_FAILURES_KEY) + 1;
        SableTrainStore store = SableTrainStore.get(server);
        if (failures >= MAX_RESTORE_FAILURES) {
            store.remove(id);
            LoconauticsConstants.LOGGER.warn("[sabletrain] deleted train record {} after {} failed restores", id,
                    failures);
            return;
        }
        record.putInt(RESTORE_FAILURES_KEY, failures);
        store.put(id, record);
    }

    private static void anchorCursorsToBodies() {
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.isGhost() || train.isDerailed() || train.isPhysics() || train.car().subLevelId() == null) {
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            if (sub == null) {
                continue;
            }
            double targetSpeed = train.targetSpeed();
            if (SableTrainSpawner.reseatOnRail(train, sub)) {
                train.setTargetSpeed(targetSpeed);
            }
        }
    }

    private static void snapshotAll(MinecraftServer server) {
        SableTrainStore store = SableTrainStore.get(server);
        for (SableTrain train : SableTrainRegistry.all()) {
            try {
                store.put(train.id(), serialize(train));
            } catch (Throwable t) {
                LoconauticsConstants.LOGGER.error("[sabletrain] snapshot failed for train {}", train.id(), t);
            }
        }
    }

    static CompoundTag serialize(SableTrain train) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", train.id());
        tag.putString("Dimension", train.level().dimension().location().toString());
        tag.putBoolean("Physics", train.isPhysics());
        tag.putBoolean("Derailed", train.isDerailed());
        if (train.owner() != null) {
            tag.putUUID("Owner", train.owner());
        }
        tag.putDouble("TargetSpeed", train.targetSpeed());
        tag.putDouble("Speed", train.speed());
        tag.putDouble("Accel", train.accel());
        tag.putBoolean("ScheduleDriven", train.isScheduleDriven());
        tag.putDouble("GhostCruise", train.ghostCruise());
        tag.putDouble("AxleSign", train.axleSign());
        train.poseTweak().save(tag);
        tag.putInt("MapColor", train.mapColorIndex());
        tag.putString("IconType", train.iconType());
        String name = SableTrainSpawner.trainName(train.level(), train);
        if (!name.isBlank()) {
            tag.putString("Name", name);
        }
        tag.putString("StationState", train.stationState().name());
        if (train.atStation() != null) {
            tag.putUUID("AtStation", train.atStation().id);
        }
        writePointer(tag, train.bodyPointer());
        if (train.extent() != null) {
            train.extent().write(tag);
        }
        if (train.bodyChunk() != null) {
            tag.putInt("BodyChunkX", train.bodyChunk().x);
            tag.putInt("BodyChunkZ", train.bodyChunk().z);
        }
        if (!train.consistCache().isEmpty()) {
            ListTag consist = new ListTag();
            for (UUID id : train.consistCache()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("SubLevel", id);
                consist.add(entry);
            }
            tag.put("Consist", consist);
        }

        DimensionPalette dims = new DimensionPalette();
        SableTrain.Car car = train.car();
        if (car.subLevelId() != null) {
            ServerSubLevel sub = SubLevelData.resolve(train.level(), car.subLevelId());
            if (sub != null) {
                writeVec3d(tag, "LastPos", SubLevelData.position(sub));
            } else if (train.isDerailed() && train.lastKnownPos() != null) {
                writeVec3d(tag, "LastPos", train.lastKnownPos());
            } else {
                Vector3d cursor = TrainResidency.railCursor(car);
                // a rail on a body that is not loaded reads in plot space, which is no place a player could look for the train
                if (cursor != null && !TrainResidency.inPlotGrid(train.level(), cursor)) {
                    writeVec3d(tag, "LastPos", cursor);
                }
            }
            CompoundTag c = new CompoundTag();
            c.putUUID("SubLevel", car.subLevelId());
            if (car.carriage() == null) {
                c.putBoolean("Trackless", true);
            } else {
                c.putUUID("Graph", car.carriage().graphId());
                c.put("Carriage", car.carriage().writeNbt(dims));
            }
            if (car.localLead() != null) {
                writeVec3d(c, "LocalLead", car.localLead());
            }
            if (car.localTrail() != null) {
                writeVec3d(c, "LocalTrail", car.localTrail());
            }
            ListTag bogeyList = new ListTag();
            for (SableTrain.Bogey bogey : car.bogeys()) {
                CompoundTag bt = new CompoundTag();
                bt.putLong("LocalPos", bogey.localPos().asLong());
                if (bogey.rail() != null) {
                    bt.putUUID("Graph", bogey.rail().graphId());
                    bt.put("Carriage", bogey.rail().writeNbt(dims));
                }
                net.minecraft.core.Direction front = train.bogeyFrontOverride(bogey.localPos());
                if (front != null) {
                    bt.putString("Front", front.getSerializedName());
                }
                bogeyList.add(bt);
            }
            c.put("Bogeys", bogeyList);
            tag.put("Car", c);
        }
        dims.write(tag); // after the car, so the palette has gathered every dimension the points reference
        return tag;
    }

    // a sub levels rails register as a new graph every load, only the node coordinates survive the saved id
    private static TrackGraph graphFor(UUID graphId, CompoundTag carriage, DimensionPalette dims) {
        TrackGraph saved = Create.RAILWAYS.trackNetworks.get(graphId);
        if (saved != null) {
            return saved;
        }
        TrackNodeLocation node = firstNode(carriage, dims);
        if (node == null) {
            return null;
        }
        for (TrackGraph candidate : Create.RAILWAYS.trackNetworks.values()) {
            if (candidate.locateNode(node) != null) {
                return candidate;
            }
        }
        return null;
    }

    private static TrackNodeLocation firstNode(CompoundTag carriage, DimensionPalette dims) {
        ListTag nodes = carriage.getCompound("Leading").getList("Nodes", Tag.TAG_COMPOUND);
        return nodes.isEmpty() ? null : TrackNodeLocation.read(nodes.getCompound(0), dims);
    }

    static SableTrain deserialize(CompoundTag tag, MinecraftServer server, boolean verbose, UUID trainId) {
        ResourceKey<Level> dimKey = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension")));
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            if (verbose) {
                LoconauticsConstants.LOGGER.warn("[sabletrain] waiting to restore {}: dimension {} not loaded",
                        trainId, tag.getString("Dimension"));
            }
            return null;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            if (verbose) {
                LoconauticsConstants.LOGGER.warn("[sabletrain] waiting to restore {}: no sub-level container yet", trainId);
            }
            return null;
        }
        DimensionPalette dims = DimensionPalette.read(tag);

        CompoundTag c;
        if (tag.contains("Car")) {
            c = tag.getCompound("Car");
        } else {
            ListTag carList = tag.getList("Cars", Tag.TAG_COMPOUND);
            if (carList.isEmpty()) {
                return null;
            }
            c = carList.getCompound(0);
        }
        UUID subId = c.getUUID("SubLevel");
        boolean trackless = c.getBoolean("Trackless");
        UUID graphId = trackless ? null : c.getUUID("Graph");

        TrackGraph graph = trackless ? null : graphFor(graphId, c.getCompound("Carriage"), dims);
        if (graph == null && !trackless) {
            if (verbose) {
                LoconauticsConstants.LOGGER.warn(
                        "[sabletrain] waiting to restore {}: track graph {} not loaded and no graph holds the rail it sat on ({} graphs present)",
                        trainId, graphId, Create.RAILWAYS.trackNetworks.size());
            }
            return null;
        }
        boolean ghost = !(container.getSubLevel(subId) instanceof ServerSubLevel);
        RailCarriage carriage = trackless ? null : RailCarriage.restore(graph, c.getCompound("Carriage"), dims);
        if (carriage == null && !trackless) {
            if (verbose) {
                LoconauticsConstants.LOGGER.warn(
                        "[sabletrain] waiting to restore {}: the rail cart {} sat on is not in the graph", trainId,
                        subId);
            }
            return null;
        }
        List<SableTrain.Bogey> bogeys = new ArrayList<>();
        Map<BlockPos, net.minecraft.core.Direction> bogeyFronts = new HashMap<>();
        ListTag bogeyList = c.getList("Bogeys", Tag.TAG_COMPOUND);
        for (int j = 0; j < bogeyList.size(); j++) {
            CompoundTag bt = bogeyList.getCompound(j);
            if (!bt.hasUUID("Graph")) {
                bogeys.add(new SableTrain.Bogey(BlockPos.of(bt.getLong("LocalPos")), null));
                continue;
            }
            TrackGraph bGraph = graphFor(bt.getUUID("Graph"), bt.getCompound("Carriage"), dims);
            if (bGraph == null) {
                return null;
            }
            RailCarriage bRail = RailCarriage.restore(bGraph, bt.getCompound("Carriage"), dims);
            if (bRail != null) {
                BlockPos localPos = BlockPos.of(bt.getLong("LocalPos"));
                bogeys.add(new SableTrain.Bogey(localPos, bRail));
                if (bt.contains("Front")) {
                    net.minecraft.core.Direction front =
                            net.minecraft.core.Direction.byName(bt.getString("Front"));
                    if (front != null) {
                        bogeyFronts.put(localPos, front);
                    }
                }
            }
        }
        Vector3dc localLead = readVec3d(c, "LocalLead");
        Vector3dc localTrail = readVec3d(c, "LocalTrail");
        SableTrain.Car car = new SableTrain.Car(subId, carriage, bogeys, localLead, localTrail);

        SableTrain train = new SableTrain(tag.getUUID("Id"), level, car, tag.getBoolean("Physics"));
        train.restoreBogeyFrontOverrides(bogeyFronts);
        train.setDerailedQuiet(tag.getBoolean("Derailed"));
        if (tag.hasUUID("Owner")) {
            train.setOwner(tag.getUUID("Owner"));
        }
        train.setTargetSpeed(tag.getDouble("TargetSpeed"));
        train.setSpeed(tag.getDouble("Speed"));
        if (tag.contains("Accel")) {
            train.setAccel(tag.getDouble("Accel"));
        }
        train.setScheduleDriven(tag.getBoolean("ScheduleDriven"));
        train.setGhostCruise(tag.getDouble("GhostCruise"));
        if (tag.contains("AxleSign")) {
            train.setAxleSign(tag.getDouble("AxleSign"));
        }
        train.poseTweak().load(tag);
        train.setMapColorIndex(tag.getInt("MapColor"));
        train.setIconType(tag.getString("IconType"));
        train.setName(tag.getString("Name"));
        // trips the guard in tickstationstate every tick, so it never latches stopped again
        if (tag.contains("StationState")) {
            try {
                train.setStationState(SableTrain.StationState.valueOf(tag.getString("StationState")));
            } catch (IllegalArgumentException unknown) {
                train.setStationState(SableTrain.StationState.RUNNING);
            }
        }
        if (tag.hasUUID("AtStation")) {
            train.setAtStation(graph.getPoint(EdgePointType.STATION, tag.getUUID("AtStation")));
        }
        train.setBodyPointer(readPointer(tag));
        train.setLastKnownPos(readVec3d(tag, "LastPos"));
        train.setExtent(CarExtent.read(tag));
        if (tag.contains("BodyChunkX")) {
            train.setBodyChunk(new net.minecraft.world.level.ChunkPos(
                    tag.getInt("BodyChunkX"), tag.getInt("BodyChunkZ")));
        }
        if (tag.contains("Consist")) {
            List<UUID> consist = new ArrayList<>();
            ListTag consistList = tag.getList("Consist", Tag.TAG_COMPOUND);
            for (int i = 0; i < consistList.size(); i++) {
                consist.add(consistList.getCompound(i).getUUID("SubLevel"));
            }
            train.setConsistCache(consist);
        }
        train.setResidency(ghost ? SableTrain.Residency.GHOST : SableTrain.Residency.EMBODIED);
        if (ghost) {
        }
        return train;
    }

    private static void writePointer(CompoundTag tag, GlobalSavedSubLevelPointer pointer) {
        if (pointer == null) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("ChunkX", pointer.chunkPos().x);
        t.putInt("ChunkZ", pointer.chunkPos().z);
        t.putShort("StorageIndex", pointer.storageIndex());
        t.putShort("SubLevelIndex", pointer.subLevelIndex());
        tag.put("BodyPointer", t);
    }

    private static GlobalSavedSubLevelPointer readPointer(CompoundTag tag) {
        if (!tag.contains("BodyPointer")) {
            return null;
        }
        CompoundTag t = tag.getCompound("BodyPointer");
        return new GlobalSavedSubLevelPointer(
                new net.minecraft.world.level.ChunkPos(t.getInt("ChunkX"), t.getInt("ChunkZ")),
                t.getShort("StorageIndex"), t.getShort("SubLevelIndex"));
    }

    private static void writeVec3d(CompoundTag tag, String key, Vector3dc v) {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", v.x());
        t.putDouble("y", v.y());
        t.putDouble("z", v.z());
        tag.put(key, t);
    }

    private static Vector3dc readVec3d(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        CompoundTag t = tag.getCompound(key);
        return new Vector3d(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
    }
}
