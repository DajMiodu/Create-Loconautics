package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.ScheduleConductor;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.SableTrain.Residency;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.spacing.TrainSpacing;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class TrainResidency {

    // a body that just came back is still at the pose it unloaded at, the write waits for the server tick, never a container callback
    private static final Set<UUID> RECONCILE = ConcurrentHashMap.newKeySet();

    private static final Set<UUID> RAILHEAD_HELD = ConcurrentHashMap.newKeySet();

    // a miss makes sable log an error per candidate chunk, so a body that cannot be found must not be hunted every tick
    private static final int SNATCH_RETRY_TICKS = 40;

    private static final Map<UUID, Integer> SNATCH_COOLDOWN = new ConcurrentHashMap<>();

    private static final Set<UUID> SNATCH_MISS_LOGGED = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Integer> SNATCH_MISSES = new ConcurrentHashMap<>();

    // 30 misses at 40 ticks each is a minute of looking, after that the body is checked on disk and in every level
    private static final int LOST_CHECK_MISSES = 30;

    private static final Map<UUID, Long> REFILED_AT = new ConcurrentHashMap<>();

    // sable returns a refiled body as soon as its own bounds are loaded, so a second refile right after would only loop
    private static final int REFILE_BACKOFF_TICKS = 100;

    private static final Map<UUID, Long> DEFER_UNTIL = new ConcurrentHashMap<>();

    private static final int DEFER_RECHECK_TICKS = 20;

    private static final Map<UUID, Long> SPLIT_PINNED = new ConcurrentHashMap<>();

    private static final Set<UUID> SPLIT_GAVE_UP = ConcurrentHashMap.newKeySet();

    // 8 cars of 9 chunks each is no more than eight stopped trains already pin at a station
    private static final int MAX_SPLIT_PINS = 8;

    private static final Map<UUID, Integer> RESEAT_FAILS = new ConcurrentHashMap<>();

    private static final int RESEAT_RETRY_TICKS = 40;

    private static final int MAX_RESEAT_FAILS = 5;

    private static final Map<UUID, Long> UNLOADED_AT = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> GHOST_STEPPED = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> EMBODIED_AT = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> PULL_BLOCKED_UNTIL = new ConcurrentHashMap<>();

    public static final long EMBODY_GRACE_TICKS = 100L;

    // a body sable took away this soon after it came back flapped, nothing pulls it back for a while
    private static final long FLAP_TICKS = 20L;

    private static final long FLAP_HOLDOFF_TICKS = 100L;

    // a mate that flaps in and out of loading is still a ghost for the heads sake, or the head drives off and tears the knuckle
    private static final long MATE_UNLOAD_HOLD_TICKS = 60L;


    public static final int POSE_SETTLE_TICKS = 20;

    private static final double RESEAT_IN_PLACE = 1.0;

    private static final int EXTENT_REFRESH_MASK = 15;

    public static void clear() {
        RECONCILE.clear();
        RAILHEAD_HELD.clear();
        SNATCH_COOLDOWN.clear();
        SNATCH_MISS_LOGGED.clear();
        SNATCH_MISSES.clear();
        REFILED_AT.clear();
        DEFER_UNTIL.clear();
        SPLIT_PINNED.clear();
        SPLIT_GAVE_UP.clear();
        RESEAT_FAILS.clear();
        UNLOADED_AT.clear();
        GHOST_STEPPED.clear();
        EMBODIED_AT.clear();
        PULL_BLOCKED_UNTIL.clear();
        TrainSpacing.clear();
    }

    private TrainResidency() {
    }

    public static void onSubLevelAdded(SubLevel subLevel) {
        SableTrain train = SableTrainRegistry.bySubLevel(subLevel.getUniqueId());
        if (train == null || !train.isGhost()) {
            return;
        }
        RECONCILE.add(train.id());
    }

    // a train restored off disk whose body was already loaded never went through onsubleveladded, nothing had it in the registry yet
    public static void queueReconcile(SableTrain train) {
        if (train != null) {
            RECONCILE.add(train.id());
        }
    }

    public static void onSubLevelUnloaded(SubLevel subLevel) {
        SableTrain train = SableTrainRegistry.bySubLevel(subLevel.getUniqueId());
        if (train == null) {
            return;
        }
        if (train.isGhost()) {
            // sable loaded this body with a mate and took it back before its turn
            RECONCILE.remove(train.id());
            return;
        }
        if (subLevel instanceof ServerSubLevel sub) {
            GlobalSavedSubLevelPointer pointer = sub.getLastSerializationPointer();
            if (pointer != null) {
                train.setBodyPointer(pointer);
            }
            train.setBodyChunk(chunkOf(sub.logicalPose().position()));
            train.setLastKnownPos(sub.logicalPose().position());
        }
        train.setResidency(Residency.GHOST);
        long unloadedAt = train.level().getGameTime();
        UNLOADED_AT.put(train.id(), unloadedAt);
        if (unloadedAt - EMBODIED_AT.getOrDefault(train.id(), Long.MIN_VALUE / 2) < FLAP_TICKS) {
            PULL_BLOCKED_UNTIL.put(train.id(), unloadedAt + FLAP_HOLDOFF_TICKS);
        }
        RECONCILE.remove(train.id());
        SableTrainDriver.forgetBodyState(train);
        // the plates hang off the car by a joint, and a joint left on a body sable took away panics rapier
        GhostWheelBodies.releaseAll(train);
        if (train.level().getServer() != null && train.level().getServer().isRunning()) {
            SableTrainPersistence.persist(train);
        }
    }

    public static void cacheEmbodiedState(SableTrain train, ServerSubLevel sub) {
        GlobalSavedSubLevelPointer pointer = sub.getLastSerializationPointer();
        if (pointer != null) {
            train.setBodyPointer(pointer);
        }
        train.setBodyChunk(chunkOf(sub.logicalPose().position()));
        train.setLastKnownPos(sub.logicalPose().position());
        train.setConsistCache(TrainConsist.connectedSubLevels(train.level(), train.car().subLevelId()));
        if (train.extent() == null || (train.level().getGameTime() & EXTENT_REFRESH_MASK) == (train.id().hashCode() & EXTENT_REFRESH_MASK)) {
            refreshExtent(train);
        }
        var controller = SableTrainDriver.controllerIn(train);
        train.setScheduleDriven(controller != null && controller.isScheduleControlled());
        double top = ConsistPower.peakTopBpt(train);
        if (top > 1.0e-4) {
            train.setGhostCruise(top);
        }
    }

    private static void refreshExtent(SableTrain train) {
        var measured = TrainLength.measure(train.level(), train);
        if (measured != null) {
            train.setExtent(measured);
        }
    }

    public static void tick(SableTrain train) {
        if (RECONCILE.contains(train.id())) {
            if (TrainSpacing.mayEmbody(train)) {
                RECONCILE.remove(train.id());
                if (embody(train) && !train.isGhost()) {
                    return;
                }
            }
            train.setResidency(Residency.GHOST);
        }
        if (train.isGhost()) {
            tickGhost(train);
            return;
        }
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            train.setResidency(Residency.GHOST);
            return;
        }
        cacheEmbodiedState(train, sub);
    }

    private static void tickGhost(SableTrain train) {
        Car car = train.car();
        if (car == null || car.subLevelId() == null) {
            return;
        }
        Vector3d cursor = railCursor(car);
        if (train.isDerailed() || train.isPhysics()) {
            // this branch never reaches the ticket update below, so a hold taken before the derail would stay forever
            if (RAILHEAD_HELD.remove(train.id()) | SPLIT_PINNED.remove(train.id()) != null) {
                StationChunkTickets.release(train.id());
            }
            ChunkPos fell = train.bodyChunk();
            if (fell != null && footprintLoaded(train.level(), fell)) {
                snatchBody(train);
            }
            return;
        }
        boolean split = hasEmbodiedMate(train);
        if (staffLockedGhost(train)) {
            train.setTargetSpeed(0.0);
            train.setSpeed(0.0);
        } else {
            SableScheduleRuntime.tick(train, null);
            TrainStations.tickStation(train, null);
            TrainSignals.tickSignals(train);
            stepGhostConsist(train, split);
        }

        cursor = railCursor(car);
        boolean loaded = footprintLoaded(train.level(), cursor) && !car.carriage().railHostMissing();
        updateSplitPin(train, cursor, split);
        StationChunkTickets.update(train);
        if (loaded) {
            snatchBody(train);
        } else {
            TrainSpacing.release(train);
        }
    }

    private static boolean staffLockedGhost(SableTrain train) {
        Set<UUID> ids = new HashSet<>(train.consistCache());
        ids.add(train.car().subLevelId());
        return ExternalForceDerail.staffLockedById(train.level(), ids);
    }

    private static void stepGhostConsist(SableTrain train, boolean split) {
        long now = train.level().getGameTime();
        if (GHOST_STEPPED.getOrDefault(train.id(), -1L) == now) {
            return;
        }
        List<SableTrain> group = GhostConsist.ghosts(train, true);
        SableTrain leader = GhostConsist.leader(group);
        if (split) {
            leader.setTargetSpeed(0.0);
        }
        leader.tickMotion();
        double[] sense = new double[group.size()];
        for (int i = 0; i < group.size(); i++) {
            SableTrain member = group.get(i);
            sense[i] = member == leader ? 1.0 : GhostConsist.senseAgainst(member, leader);
            if (member != leader) {
                member.setTargetSpeed(leader.targetSpeed() * sense[i]);
                member.setSpeed(leader.speed() * sense[i]);
            }
        }
        double ds = TrainSpacing.ghostStep(leader);
        boolean end = false;
        for (SableTrain member : group) {
            end |= TrainRailAdvance.anyReachedEnd(member.car()) || TrainRailAdvance.anyPendingPortal(member.car());
        }
        if (end) {
            ds = 0.0;
        }
        for (int i = 0; i < group.size(); i++) {
            SableTrain member = group.get(i);
            if (end) {
                RAILHEAD_HELD.add(member.id());
            } else {
                RAILHEAD_HELD.remove(member.id());
            }
            if (member != leader) {
                member.setSpeed(leader.speed() * sense[i]);
            }
            if (ds != 0.0) {
                TrainRailAdvance.advanceRail(member.car(), ds * sense[i]);
            }
            GHOST_STEPPED.put(member.id(), now);
        }
    }

    // a ghost car with an embodied mate stands still waiting for chunks nobody loads, so it pins its own, a few cars at a time
    private static void updateSplitPin(SableTrain train, Vector3d cursor, boolean split) {
        UUID id = train.id();
        if (!split) {
            SPLIT_PINNED.remove(id);
            SPLIT_GAVE_UP.remove(id);
            return;
        }
        long now = train.level().getGameTime();
        Long since = SPLIT_PINNED.get(id);
        if (since != null) {
            boolean lost = bodyLost(train);
            if (lost || now - since > StationChunkTickets.MAX_DWELL_TICKS) {
                SPLIT_PINNED.remove(id);
                SPLIT_GAVE_UP.add(id);
            }
            return;
        }
        if (SPLIT_GAVE_UP.contains(id) || bodyLost(train) || cursor == null || inPlotGrid(train.level(), cursor)) {
            return;
        }
        int pinned = 0;
        for (UUID mate : train.consistCache()) {
            if (SPLIT_PINNED.containsKey(mate)) {
                pinned++;
            }
        }
        if (pinned >= MAX_SPLIT_PINS) {
            return;
        }
        SPLIT_PINNED.put(id, now);
    }

    public static boolean bodyLost(SableTrain train) {
        return SNATCH_MISSES.getOrDefault(train.id(), 0) >= LOST_CHECK_MISSES;
    }

    private static void snatchBody(SableTrain train) {
        Integer wait = SNATCH_COOLDOWN.get(train.id());
        if (wait != null) {
            if (wait > 1) {
                SNATCH_COOLDOWN.put(train.id(), wait - 1);
                return;
            }
            SNATCH_COOLDOWN.remove(train.id());
        }
        String notReady = matesNotReady(train);
        if (notReady != null) {
            return;
        }
        if (!TrainSpacing.mayEmbody(train)) {
            return;
        }
        if (resolve(train) != null) {
            RECONCILE.remove(train.id());
            embody(train);
            embodyMates(train);
            return;
        }
        if (pullBlocked(train)) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        UUID subId = train.car().subLevelId();
        SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        HoldingSubLevel holding = map.getHoldingSubLevel(subId);
        GlobalSavedSubLevelPointer pointer = holding != null ? holding.pointer() : train.bodyPointer();
        List<ChunkPos> candidates = candidateChunks(train, holding);
        if (candidates.isEmpty()) {
            return;
        }
        short storageIndex = pointer == null ? 0 : pointer.storageIndex();
        short subLevelIndex = pointer == null ? 0 : pointer.subLevelIndex();
        for (ChunkPos candidate : candidates) {
            map.snatchAndLoad(new GlobalSavedSubLevelPointer(candidate, storageIndex, subLevelIndex), subId);
            if (resolve(train) == null) {
                continue;
            }
            train.setBodyChunk(candidate);
            RECONCILE.remove(train.id());
            embody(train);
            embodyMates(train);
            return;
        }
        SNATCH_COOLDOWN.put(train.id(), SNATCH_RETRY_TICKS);
        int misses = SNATCH_MISSES.merge(train.id(), 1, Integer::sum);
        if (SNATCH_MISS_LOGGED.add(train.id())) {
            LoconauticsConstants.LOGGER.warn("[residency] body {} of train {} was not found in any of {} candidate "
                    + "holding chunks, pointer {}, bodyChunk {}, holdingKnown={}, misses={}", shortId(subId),
                    shortId(train.id()), candidates.size(), pointer, train.bodyChunk(), holding != null, misses);
            LoconauticsConstants.LOGGER.warn("[ghostedge] miss train={} candidates={} storedBounds={} grownBounds={}",
                    shortId(train.id()), candidates,
                    holding == null ? "none" : boundsText(holding.data().bounds(), 0.0),
                    holding == null ? "none" : boundsText(holding.data().bounds(), 1.0));
        }
        if (misses == LOST_CHECK_MISSES || (misses > LOST_CHECK_MISSES && misses % (LOST_CHECK_MISSES * 10) == 0)) {
            LoconauticsConstants.LOGGER.warn("[residency] body {} of train {} is still missing after {} misses, {}",
                    shortId(subId), shortId(train.id()), misses, lostReport(train, container, subId, pointer));
        }
    }

    // sable loads a body with its chain, an unready car would pull them all back down
    private static String matesNotReady(SableTrain train) {
        for (SableTrain mate : GhostConsist.ghosts(train, true)) {
            if (mate == train || bodyLost(mate)) {
                continue;
            }
            Vector3d at = railCursor(mate.car());
            if (at == null || !footprintLoaded(mate.level(), at)) {
                return shortId(mate.id()) + (at == null ? " has no rail point" : " has unloaded chunks at " + v3(at));
            }
        }
        return null;
    }

    private static void embodyMates(SableTrain train) {
        if (train.isGhost()) {
            return;
        }
        for (SableTrain mate : GhostConsist.ghosts(train, false)) {
            if (mate != train && resolve(mate) != null) {
                RECONCILE.remove(mate.id());
                embody(mate);
            }
        }
    }

    private static String lostReport(SableTrain train, ServerSubLevelContainer container, UUID subId,
                                     GlobalSavedSubLevelPointer pointer) {
        StringBuilder out = new StringBuilder();
        MinecraftServer server = train.level().getServer();
        boolean live = false;
        if (server != null) {
            for (ServerLevel other : server.getAllLevels()) {
                ServerSubLevelContainer otherContainer = SubLevelContainer.getContainer(other);
                if (otherContainer != null && otherContainer.getSubLevel(subId) != null) {
                    out.append("live in ").append(other.dimension().location()).append(", ");
                    live = true;
                }
            }
        }
        if (!live) {
            out.append("not live in any level, ");
        }
        List<GlobalSavedSubLevelPointer> pointers = new ArrayList<>(2);
        if (pointer != null) {
            pointers.add(pointer);
        }
        if (train.bodyPointer() != null && !train.bodyPointer().equals(pointer)) {
            pointers.add(train.bodyPointer());
        }
        if (pointers.isEmpty()) {
            out.append("no saved pointer to check");
        }
        for (GlobalSavedSubLevelPointer saved : pointers) {
            SubLevelData data = container.getHoldingChunkMap().getStorage()
                    .attemptLoadSubLevel(saved.chunkPos(), saved.local());
            out.append("disk ").append(saved).append(data == null ? " has nothing"
                    : data.uuid().equals(subId) ? " holds this body" : " holds body " + shortId(data.uuid()))
                    .append(", ");
        }
        return out.toString();
    }

    private static List<ChunkPos> candidateChunks(SableTrain train, HoldingSubLevel holding) {
        List<ChunkPos> out = new ArrayList<>();
        ChunkPos primary = holding != null ? chunkOf(new Vector3d(holding.data().pose().position()))
                : train.bodyChunk();
        if (primary != null) {
            out.add(primary);
        }
        if (holding != null) {
            // sable files a body under a chunk of its bounds grown by one block, the same margin its ready test uses
            BoundingBox3d bounds = holding.data().bounds();
            addSpan(out, Mth.floor(bounds.minX() - 1.0) >> 4, Mth.floor(bounds.maxX() + 1.0) >> 4,
                    Mth.floor(bounds.minZ() - 1.0) >> 4, Mth.floor(bounds.maxZ() + 1.0) >> 4);
        } else if (primary != null) {
            addSpan(out, primary.x - 1, primary.x + 1, primary.z - 1, primary.z + 1);
        }
        if (train.bodyChunk() != null && !out.contains(train.bodyChunk())) {
            out.add(train.bodyChunk());
        }
        if (train.bodyPointer() != null && !out.contains(train.bodyPointer().chunkPos())) {
            out.add(train.bodyPointer().chunkPos());
        }
        return out;
    }

    private static void addSpan(List<ChunkPos> out, int minX, int maxX, int minZ, int maxZ) {
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                if (!out.contains(pos)) {
                    out.add(pos);
                }
            }
        }
    }

    private static ChunkPos chunkOf(Vector3dc pos) {
        return new ChunkPos((int) Math.floor(pos.x()) >> 4, (int) Math.floor(pos.z()) >> 4);
    }

    private static boolean embody(SableTrain train) {
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return true;
        }
        Vector3d origin = new Vector3d();
        Quaterniond orient = SableTrainDriver.seatPose(sub, train, origin);
        boolean reseat = !train.isDerailed() && !train.isPhysics() && orient != null;
        if (reseat) {
            Target target = guardTarget(train, sub, origin, orient);
            if (target == Target.DEFERRED) {
                RECONCILE.add(train.id());
                return false;
            }
            if (target == Target.PARKED) {
                return true;
            }
        }
        SableTrainDriver.forgetBodyState(train);
        train.beginPoseSettle(POSE_SETTLE_TICKS);
        boolean seated = false;
        if (reseat) {
            Vector3d before = new Vector3d(sub.logicalPose().position());
            if (before.distance(origin) <= RESEAT_IN_PLACE) {
                SableTeleportation.writePose(train.level(), sub, origin, orient);
                seated = true;
            } else {
                SableTeleportation.Result placed = SableTeleportation.teleport(sub, train.level(), origin, orient);
                if (placed.ok()) {
                    sub = placed.moved();
                    seated = true;
                } else {
                    if (retryReseat(train, placed.failure())) {
                        return false;
                    }
                    // the failed teleport may have put the body back as a new object, so the old handle is stale
                    ServerSubLevel back = resolve(train);
                    if (back == null) {
                        return true;
                    }
                    sub = back;
                }
            }
        }
        if (seated) {
            // sable gives back 0.9 of the saved speed or none, the rail speed is what the train really has
            SableTrainDriver.seedRailVelocity(sub, train.car(), train);
        }
        zeroLeverOfStoppedGhost(train, sub);
        train.setResidency(Residency.EMBODIED);
        EMBODIED_AT.put(train.id(), train.level().getGameTime());
        SNATCH_COOLDOWN.remove(train.id());
        SNATCH_MISSES.remove(train.id());
        SNATCH_MISS_LOGGED.remove(train.id());
        DEFER_UNTIL.remove(train.id());
        RESEAT_FAILS.remove(train.id());
        SPLIT_PINNED.remove(train.id());
        SPLIT_GAVE_UP.remove(train.id());
        RAILHEAD_HELD.remove(train.id());
        StationChunkTickets.clearCap(train.id());
        GhostWheelBodies.rebuild(train);
        cacheEmbodiedState(train, sub);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
        ScheduleConductor.broadcastBlazeHats(sub);
        return true;
    }

    // the lever is saved with the body and would drive a parked ghost off the moment it loads
    private static void zeroLeverOfStoppedGhost(SableTrain train, ServerSubLevel sub) {
        if (Math.abs(train.targetSpeed()) > 1.0e-6 && Math.abs(train.speed()) > StationLanding.PARK_SPEED) {
            return;
        }
        for (AnalogControllerBlockEntity controller : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
            if (controller.getCurrentPower() != 0 && !controller.hasUser()) {
                controller.setCurrentPower(0);
            }
        }
    }

    private static boolean retryReseat(SableTrain train, String failure) {
        UUID id = train.id();
        int fails = RESEAT_FAILS.merge(id, 1, Integer::sum);
        LoconauticsConstants.LOGGER.warn("[ghost] {} could not be reseated off disk: {} [ghostedge] attempt {} of {}",
                shortId(id), failure, fails, MAX_RESEAT_FAILS);
        if (fails >= MAX_RESEAT_FAILS) {
            RESEAT_FAILS.remove(id);
            return false;
        }
        train.beginPoseSettle(0);
        DEFER_UNTIL.put(id, train.level().getGameTime() + RESEAT_RETRY_TICKS);
        RECONCILE.add(id);
        return true;
    }

    private enum Target { PROCEED, PARKED, DEFERRED }

    // a body sable brought back at its old place is never reseated onto a cursor in chunks nobody has loaded
    private static Target guardTarget(SableTrain train, ServerSubLevel sub, Vector3d origin, Quaterniond orient) {
        if (sub.logicalPose().position().distance(origin) <= RESEAT_IN_PLACE) {
            return Target.PROCEED;
        }
        ServerLevel level = train.level();
        UUID id = train.id();
        long now = level.getGameTime();
        Long until = DEFER_UNTIL.get(id);
        if (until != null && now < until) {
            return Target.DEFERRED;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Target.PROCEED;
        }
        boolean blind = train.car() != null && train.car().carriage().railHostMissing();
        if (!blind && targetLoaded(level, sub, origin)) {
            DEFER_UNTIL.remove(id);
            return Target.PROCEED;
        }
        boolean aboard = !blind && playerAboard(level, sub);
        if (blind || aboard) {
            DEFER_UNTIL.put(id, now + DEFER_RECHECK_TICKS);
            return Target.DEFERRED;
        }
        DEFER_UNTIL.remove(id);
        Long last = REFILED_AT.get(id);
        if (last != null && now - last < REFILE_BACKOFF_TICKS) {
            return Target.PROCEED;
        }
        if (hasEmbodiedMate(train)) {
            return Target.PROCEED;
        }
        int chain = SubLevelHelper.getLoadingDependencyChain(sub).size();
        if (chain > 1) {
            return Target.PROCEED;
        }
        refile(train, sub, container, origin, orient, now);
        return Target.PARKED;
    }

    private static boolean targetLoaded(ServerLevel level, ServerSubLevel sub, Vector3d origin) {
        BoundingBox3dc box = sub.boundingBox();
        Vector3d at = sub.logicalPose().position();
        double dx = origin.x - at.x;
        double dz = origin.z - at.z;
        int minX = Mth.floor(box.minX() + dx - 1.0) >> 4;
        int maxX = Mth.floor(box.maxX() + dx + 1.0) >> 4;
        int minZ = Mth.floor(box.minZ() + dz - 1.0) >> 4;
        int maxZ = Mth.floor(box.maxZ() + dz + 1.0) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    // sable keeps a body with a player inside its box, so the check is the same box grown by one block
    private static boolean playerAboard(ServerLevel level, ServerSubLevel sub) {
        BoundingBox3dc box = sub.boundingBox();
        return !level.getPlayers(player -> {
            Vec3 c = player.getBoundingBox().getCenter();
            return c.x >= box.minX() - 1.0 && c.x <= box.maxX() + 1.0
                    && c.y >= box.minY() - 1.0 && c.y <= box.maxY() + 1.0
                    && c.z >= box.minZ() - 1.0 && c.z <= box.maxZ() + 1.0;
        }).isEmpty();
    }

    private static void refile(SableTrain train, ServerSubLevel sub, ServerSubLevelContainer container,
                               Vector3d origin, Quaterniond orient, long now) {
        UUID subId = sub.getUniqueId();
        ChunkPos filing = chunkOf(origin);
        GhostWheelBodies.releaseAll(train);
        SableTrainDriver.forgetBodyState(train);
        SableTeleportation.writePose(train.level(), sub, origin, orient);
        sub.updateBoundingBox();
        SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        map.moveToUnloaded(sub, filing);
        train.setBodyChunk(filing);
        HoldingSubLevel filed = map.getHoldingSubLevel(subId);
        if (filed != null && filed.pointer() != null) {
            train.setBodyPointer(filed.pointer());
        }
        REFILED_AT.put(train.id(), now);
        SableTrainPersistence.persist(train);
    }

    private static String boundsText(BoundingBox3dc box, double grow) {
        return String.format(Locale.ROOT, "x %.1f to %.1f, z %.1f to %.1f", box.minX() - grow, box.maxX() + grow,
                box.minZ() - grow, box.maxZ() + grow);
    }

    private static String v3(Vector3dc v) {
        return String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)", v.x(), v.y(), v.z());
    }

    private static boolean hasEmbodiedMate(SableTrain train) {
        Set<UUID> consist = train.consistCache();
        if (consist.size() <= 1) {
            return false;
        }
        for (UUID id : consist) {
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other != null && other != train && !other.isGhost()) {
                return true;
            }
        }
        return false;
    }

    public static boolean embodiedWithin(SableTrain train, long ticks) {
        return !train.isGhost()
                && train.level().getGameTime() - EMBODIED_AT.getOrDefault(train.id(), Long.MIN_VALUE / 2) < ticks;
    }

    public static boolean pullBlocked(SableTrain train) {
        Long until = PULL_BLOCKED_UNTIL.get(train.id());
        return until != null && train.level().getGameTime() < until;
    }

    public static Set<ChunkPos> chainChunks(SableTrain train) {
        Set<ChunkPos> out = new HashSet<>();
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return out;
        }
        for (ServerSubLevel body : SubLevelHelper.getLoadingDependencyChain(sub)) {
            BoundingBox3dc box = body.boundingBox();
            int minX = Mth.floor(box.minX() - 1.0) >> 4;
            int maxX = Mth.floor(box.maxX() + 1.0) >> 4;
            int minZ = Mth.floor(box.minZ() - 1.0) >> 4;
            int maxZ = Mth.floor(box.maxZ() + 1.0) >> 4;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    out.add(new ChunkPos(cx, cz));
                }
            }
        }
        return out;
    }

    public static boolean holdForGhostedConsist(SableTrain train) {
        if (train.isGhost()) {
            return false;
        }
        TrainSpacing.limitLoaded(train);
        Set<UUID> consist = train.consistCache();
        if (consist.size() <= 1) {
            return false;
        }
        long now = train.level().getGameTime();
        for (UUID id : consist) {
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other != null && other != train && (other.isGhost()
                    || now - UNLOADED_AT.getOrDefault(other.id(), Long.MIN_VALUE / 2) < MATE_UNLOAD_HOLD_TICKS)) {
                train.setTargetSpeed(0.0);
                return true;
            }
        }
        return false;
    }

    public static String forceGhost(SableTrain train) {
        if (train.isGhost()) {
            return shortId(train.id()) + " is already a ghost";
        }
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return shortId(train.id()) + " has no body to unload";
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return "no sub level container in " + train.level().dimension().location();
        }
        cacheEmbodiedState(train, sub);
        container.getHoldingChunkMap().moveToUnloaded(sub, chunkOf(sub.logicalPose().position()));
        return shortId(train.id()) + " body pushed to the holding chunk, it should be a ghost now";
    }

    public static BlockPos ghostHoldAnchor(SableTrain train) {
        if (!train.isGhost() || train.car() == null
                || !RAILHEAD_HELD.contains(train.id()) && !SPLIT_PINNED.containsKey(train.id())) {
            return null;
        }
        Vector3d cursor = railCursor(train.car());
        if (cursor == null || inPlotGrid(train.level(), cursor)) {
            return null;
        }
        return BlockPos.containing(cursor.x, cursor.y, cursor.z);
    }

    public static boolean inPlotGrid(ServerLevel level, Vector3d pos) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(pos);
    }

    public static Vector3d railCursor(Car car) {
        Vector3d center = new Vector3d();
        return SableTrainDriver.bodyPose(car, center) == null ? null : center;
    }

    public static boolean footprintLoaded(ServerLevel level, Vector3d pos) {
        if (level == null || pos == null) {
            return false;
        }
        return footprintLoaded(level, chunkOf(pos));
    }

    private static boolean footprintLoaded(ServerLevel level, ChunkPos center) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && container.inBounds(center)) {
            return false;
        }
        for (int cx = center.x - 1; cx <= center.x + 1; cx++) {
            for (int cz = center.z - 1; cz <= center.z + 1; cz++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void forget(Set<UUID> live) {
        RECONCILE.retainAll(live);
        SNATCH_COOLDOWN.keySet().retainAll(live);
        SNATCH_MISSES.keySet().retainAll(live);
        SNATCH_MISS_LOGGED.retainAll(live);
        REFILED_AT.keySet().retainAll(live);
        DEFER_UNTIL.keySet().retainAll(live);
        SPLIT_PINNED.keySet().retainAll(live);
        SPLIT_GAVE_UP.retainAll(live);
        RESEAT_FAILS.keySet().retainAll(live);
        RAILHEAD_HELD.retainAll(live);
        UNLOADED_AT.keySet().retainAll(live);
        GHOST_STEPPED.keySet().retainAll(live);
        EMBODIED_AT.keySet().retainAll(live);
        PULL_BLOCKED_UNTIL.keySet().retainAll(live);
        TrainSpacing.forget(live);
    }

    public static String describe(SableTrain train) {
        return shortId(train.id()) + " " + train.residency()
                + " pointer=" + train.bodyPointer()
                + " bodyChunk=" + train.bodyChunk()
                + " consist=" + train.consistCache().size()
                + " splitPin=" + SPLIT_PINNED.containsKey(train.id())
                + " scheduleDriven=" + train.isScheduleDriven()
                + " cruise=" + String.format("%.3f", train.ghostCruise());
    }

    public static String traceFlags(SableTrain train) {
        UUID id = train.id();
        Long deferred = DEFER_UNTIL.get(id);
        return String.format(Locale.ROOT, "railheadHeld=%s splitPin=%s gaveUp=%s reconcile=%s snatchMisses=%d lost=%s "
                        + "deferTicksLeft=%d reseatFails=%d", RAILHEAD_HELD.contains(id),
                SPLIT_PINNED.containsKey(id), SPLIT_GAVE_UP.contains(id), RECONCILE.contains(id),
                SNATCH_MISSES.getOrDefault(id, 0), bodyLost(train),
                deferred == null ? 0L : Math.max(0L, deferred - train.level().getGameTime()),
                RESEAT_FAILS.getOrDefault(id, 0));
    }

    private static ServerSubLevel resolve(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return null;
        }
        UUID subId = train.car() == null ? null : train.car().subLevelId();
        if (subId == null || !(container.getSubLevel(subId) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        return sub;
    }
}
