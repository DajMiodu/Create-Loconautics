package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SubLevelSplitWatcher {

    static final Set<ServerSubLevelContainer> OBSERVED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    // preexisting is a snapshot of sub level ids at split time, since sables getsplitfromsublevel is already null by our tick
    record PendingSplit(ServerLevel level, UUID originalSubId, Set<UUID> preexisting) {}

    static final List<PendingSplit> PENDING_SPLITS = new ArrayList<>();

    static boolean splitListenerRegistered;

    // true while sables own unload walk is inside our observer, taking a sub level out there kills it
    private static boolean inRemovalCallback;

    public static boolean inRemovalCallback() {
        return inRemovalCallback;
    }

    public static void registerSplitListener() {
        if (splitListenerRegistered) {
            return;
        }
        splitListenerRegistered = true;
        dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager.addSplitListener((lvl, bounds, blocks) -> {
            if (!(lvl instanceof ServerLevel sl) || blocks.isEmpty()) {
                return;
            }
            BlockPos any = blocks.iterator().next();
            if (!(Sable.HELPER.getContaining(lvl, any) instanceof ServerSubLevel orig) || orig.isRemoved()) {
                return;
            }
            UUID origId = orig.getUniqueId();
            if (SableTrainRegistry.bySubLevel(origId) == null) {
                return;
            }
            synchronized (PENDING_SPLITS) {
                for (PendingSplit p : PENDING_SPLITS) {
                    if (p.level() == sl && origId.equals(p.originalSubId())) {
                        return;
                    }
                }
                Set<UUID> preexisting = new HashSet<>();
                ServerSubLevelContainer container = SubLevelContainer.getContainer(sl);
                if (container != null) {
                    for (SubLevel s : container.getAllSubLevels()) {
                        preexisting.add(s.getUniqueId());
                    }
                }
                PENDING_SPLITS.add(new PendingSplit(sl, origId, preexisting));
            }
        });
    }

    public static void processPendingSplits() {
        List<PendingSplit> batch;
        synchronized (PENDING_SPLITS) {
            if (PENDING_SPLITS.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(PENDING_SPLITS);
            PENDING_SPLITS.clear();
        }
        for (PendingSplit ps : batch) {
            ServerLevel level = ps.level();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            UUID origId = ps.originalSubId();
            SableTrain originalTrain = SableTrainRegistry.bySubLevel(origId);
            Vec3 railDir = originalTrain != null && !originalTrain.isTrackless()
                    ? originalTrain.car().carriage().forward()
                    : new Vec3(1.0, 0.0, 0.0);
            RailCarriage referenceRail = originalTrain != null ? originalTrain.car().carriage() : null;
            ServerSubLevel origSub = originalTrain == null ? null : SubLevelData.resolve(level, origId);

            List<SableTrain.Bogey> donors = originalTrain != null
                    ? new ArrayList<>(originalTrain.car().bogeys())
                    : new ArrayList<>();

            Set<UUID> preexisting = ps.preexisting();
            for (SubLevel s : new ArrayList<>(container.getAllSubLevels())) {
                if (!(s instanceof ServerSubLevel island) || island.isRemoved()) {
                    continue;
                }
                if (preexisting.contains(island.getUniqueId())) {
                    continue;
                }
                if (SableTrainRegistry.bySubLevel(island.getUniqueId()) != null) {
                    continue;
                }
                if (com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.isWheelBody(island.getUniqueId())) {
                    continue;
                }
                if (com.lycoris.loconautics.trainphysics.portal.PortalTwins.isTwin(island)) {
                    continue;
                }
                clearOrphanPads(island);
                List<BlockPos> offTrackLocal = new ArrayList<>();
                SableTrain.Car newCar = buildCarFromSubLevelScan(level, island, railDir, donors, referenceRail,
                        originalTrain, origSub, offTrackLocal);
                boolean trackless = newCar == null && originalTrain != null && originalTrain.isTrackless();
                if (trackless) {
                    newCar = TracklessCars.build(island);
                }
                if (newCar == null) {
                    continue;
                }
                UUID id = UUID.randomUUID();
                SableTrain newTrain = new SableTrain(id, level, newCar,
                        originalTrain != null && originalTrain.isPhysics());
                newTrain.setOwner(originalTrain == null ? null : originalTrain.owner());
                newTrain.setTargetSpeed(0.0);
                if (trackless) {
                    newTrain.setDerailedQuiet(true);
                }
                for (BlockPos pos : offTrackLocal) {
                    newTrain.markBogeyOffTrack(pos);
                }
                if (!offTrackLocal.isEmpty() && offTrackLocal.size() >= newCar.bogeys().size()) {
                    // every bogey this island got was already derailed before the break, it never had a rail to begin with
                    newTrain.setDerailedQuiet(true);
                }
                SableTrainRegistry.register(newTrain);
                settleAfterSplit(newTrain);
                SableTrainPersistence.persist(newTrain);
                SableTrainSyncPacket.broadcast(newTrain);
            }

            if (originalTrain != null) {
                rebuildOriginalAfterSplit(level, originalTrain, railDir, donors, origSub);
            }
        }
    }

    // sable moves the carved bodys pose and center of mass, so the old glue and wheel plate anchors point at air
    static void settleAfterSplit(SableTrain train) {
        SableTrainDriver.forgetBodyState(train);
        com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.rebuild(train);
        train.beginPoseSettle(com.lycoris.loconautics.trainphysics.state.TrainResidency.POSE_SETTLE_TICKS);
    }

    private static final Set<UUID> BOGEYLESS_SUSPECTS = new HashSet<>();

    // a bogey broken or lost without a split event never reaches the split path, so the train would outlive its last bogey
    public static void dropBogeylessTrains() {
        Set<UUID> seen = new HashSet<>();
        for (SableTrain train : new ArrayList<>(SableTrainRegistry.all())) {
            if (train.isGhost() || train.car() == null) {
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            if (sub == null || sub.isRemoved() || sub.getPlot().getLoadedChunks().isEmpty()) {
                continue;
            }
            if (!scanBogeyPlotPositions(sub.getPlot()).isEmpty()) {
                continue;
            }
            seen.add(train.id());
            if (BOGEYLESS_SUSPECTS.contains(train.id())) {
                SubLevelMergeWatcher.dropTrain(train.level(), train);
                seen.remove(train.id());
            }
        }
        BOGEYLESS_SUSPECTS.retainAll(seen);
        BOGEYLESS_SUSPECTS.addAll(seen);
    }

    // a bogey placed into a body that is already a train is in no car list, so it never ran until the body was rebuilt
    public static void adoptNewBogeys() {
        for (SableTrain train : new ArrayList<>(SableTrainRegistry.all())) {
            if (train.isGhost() || train.car() == null) {
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            if (sub == null || sub.isRemoved() || sub.getPlot().getLoadedChunks().isEmpty()) {
                continue;
            }
            List<SableTrain.Bogey> known = train.car().bogeys();
            BlockPos plotCenter = sub.getPlot().getCenterBlock();
            List<BlockPos> found = scanBogeyPlotPositions(sub.getPlot());
            Set<BlockPos> ignored = IGNORED_BOGEYS.computeIfAbsent(train.id(), id -> new HashSet<>());
            ignored.retainAll(found);
            // a bogey placed before the car reached a rail stays ignored forever, so give it another go every two seconds
            long now = train.level().getGameTime();
            Long lastRetry = IGNORED_RETRY_AT.get(train.id());
            if (lastRetry == null || now - lastRetry >= IGNORED_RETRY_TICKS) {
                IGNORED_RETRY_AT.put(train.id(), now);
                ignored.clear();
            }
            List<BlockPos> fresh = new ArrayList<>();
            for (BlockPos plotPos : found) {
                BlockPos local = plotPos.subtract(plotCenter);
                if (!ignored.contains(plotPos) && known.stream().noneMatch(b -> b.localPos().equals(local))) {
                    fresh.add(plotPos);
                }
            }
            if (fresh.isEmpty()) {
                continue;
            }
            if (train.isTrackless()) {
                adoptTrackless(train, sub);
                continue;
            }
            List<BlockPos> accepted = new ArrayList<>();
            for (BlockPos plotPos : fresh) {
                Vector3d world = SubLevelData.worldOfOffset(sub, plotPos.subtract(plotCenter));
                BogeySeat seat = seatAdopted(train.level(), sub, known, world, train.car().carriage().forward());
                if (seat != null) {
                    accepted.add(plotPos);
                } else {
                    ignored.add(plotPos);
                }
            }
            if (accepted.isEmpty()) {
                continue;
            }
            Vec3 railDir = train.car().carriage().forward();
            SableTrain.Car rebuilt = buildCarFromSubLevelScan(train.level(), sub, railDir, new ArrayList<>(known),
                    train.car().carriage(), train, sub, new ArrayList<>(), ADOPT_MATCH_RADIUS_SQ,
                    new Adoption(known, ignored), false);
            if (rebuilt == null || !takesBogeys(known, rebuilt.bogeys())) {
                ignored.addAll(accepted);
                SableTrainSyncPacket.broadcast(train);
                continue;
            }
            train.replaceCar(rebuilt);
            SableTrainPersistence.persist(train);
            SableTrainSyncPacket.broadcast(train);
        }
        IGNORED_BOGEYS.keySet().removeIf(id -> SableTrainRegistry.get(id) == null);
        IGNORED_RETRY_AT.keySet().removeIf(id -> SableTrainRegistry.get(id) == null);
    }

    static void refitAfterBogeyDrop(SableTrain train, ServerSubLevel sub, List<SableTrain.Bogey> alive) {
        if (alive.isEmpty() || train.isTrackless() || train.car().carriage() == null) {
            return;
        }
        SableTrain.Car rebuilt = buildCarFromSubLevelScan(train.level(), sub, train.car().carriage().forward(),
                new ArrayList<>(alive), train.car().carriage(), train, sub, new ArrayList<>(), ADOPT_MATCH_RADIUS_SQ,
                null, false);
        Set<BlockPos> keptPositions = alive.stream().map(SableTrain.Bogey::localPos).collect(java.util.stream.Collectors.toSet());
        if (rebuilt == null || !rebuilt.bogeys().stream().allMatch(b -> keptPositions.contains(b.localPos()))) {
            return;
        }
        train.replaceCar(rebuilt);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
    }

    private static boolean takesBogeys(List<SableTrain.Bogey> known, List<SableTrain.Bogey> rebuilt) {
        if (rebuilt.size() != known.size()) {
            return rebuilt.size() > known.size();
        }
        return !rebuilt.stream().map(SableTrain.Bogey::localPos).collect(java.util.stream.Collectors.toSet())
                .equals(known.stream().map(SableTrain.Bogey::localPos).collect(java.util.stream.Collectors.toSet()));
    }

    private static final Map<UUID, Set<BlockPos>> IDLE_CACHE = new HashMap<>();

    private static final Map<UUID, Long> IDLE_CACHED_AT = new HashMap<>();

    public static Set<BlockPos> idleBogeysCached(SableTrain train) {
        long now = train.level().getGameTime();
        Long at = IDLE_CACHED_AT.get(train.id());
        if (at == null || now - at >= 20L) {
            IDLE_CACHE.put(train.id(), idleBogeysOnRail(train));
            IDLE_CACHED_AT.put(train.id(), now);
        }
        return IDLE_CACHE.get(train.id());
    }

    public static RailCarriage idleBogeyRail(SableTrain train, ServerSubLevel sub, BlockPos local) {
        BogeySeat seat = seatOnOwnRail(train.car().chain(), SubLevelData.worldOfOffset(sub, local), sub);
        return seat == null || seat.lateral() > INLINE_TOLERANCE ? null : seat.rail();
    }

    public static Set<BlockPos> idleBogeysOnRail(SableTrain train) {
        Set<BlockPos> idle = new HashSet<>();
        ServerSubLevel sub = train.car() == null ? null : SubLevelData.resolve(train.level(), train.car().subLevelId());
        if (sub == null || sub.isRemoved() || train.isTrackless()) {
            return idle;
        }
        BlockPos plotCenter = sub.getPlot().getCenterBlock();
        Set<BlockPos> working = new HashSet<>();
        train.car().bogeys().forEach(b -> working.add(b.localPos()));
        Vec3 railDir = train.car().carriage().forward();
        for (BlockPos plotPos : scanBogeyPlotPositions(sub.getPlot())) {
            BlockPos local = plotPos.subtract(plotCenter);
            if (working.contains(local)) {
                continue;
            }
            Vector3d world = SubLevelData.worldOfOffset(sub, local);
            SableTrainSpawner.TrackHit hit = SableTrainSpawner.findRailBelow(train.level(),
                    BlockPos.containing(world.x, world.y, world.z), railDir);
            RailCarriage rail = hit == null ? null : RailCarriage.at(hit.location(), hit.upNormal(), 1.0, 0.0);
            if (rail != null && !floatsAboveRail(rail, world)) {
                idle.add(local);
            }
        }
        return idle;
    }

    private static void adoptTrackless(SableTrain train, ServerSubLevel sub) {
        SableTrain.Car rebuilt = TracklessCars.build(sub);
        if (rebuilt == null || rebuilt.bogeys().size() <= train.car().bogeys().size()) {
            return;
        }
        train.replaceCar(rebuilt);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
    }

    static void rebuildOriginalAfterSplit(ServerLevel level, SableTrain originalTrain, Vec3 railDir,
                                          List<SableTrain.Bogey> donors, ServerSubLevel origSub) {
        UUID subId = originalTrain.car().subLevelId();
        ServerSubLevel sub = SubLevelData.resolve(level, subId);
        if (sub == null) {
            return;
        }
        clearOrphanPads(sub);
        if (originalTrain.isTrackless()) {
            SableTrain.Car rebuilt = TracklessCars.build(sub);
            if (rebuilt != null) {
                originalTrain.replaceCar(rebuilt);
                SableTrainPersistence.persist(originalTrain);
                SableTrainSyncPacket.broadcast(originalTrain);
            }
            return;
        }
        Vec3 wasChord = TrainRailAdvance.bogeyChordForward(originalTrain.car().chain());
        List<BlockPos> offTrackLocal = new ArrayList<>();
        SableTrain.Car rebuilt = buildCarFromSubLevelScan(level, sub, railDir, donors,
                originalTrain.car().carriage(), originalTrain, origSub, offTrackLocal);
        if (rebuilt != null && !rebuilt.bogeys().isEmpty()) {
            boolean keepMotion = originalTrain.isPoseSettling();
            Vec3 nowChord = TrainRailAdvance.bogeyChordForward(rebuilt.chain());
            double speed = originalTrain.speed();
            if (wasChord != null && nowChord != null && wasChord.dot(nowChord) < 0.0) {
                speed = -speed;
            }
            double targetSpeed = originalTrain.targetSpeed();
            SableTrain.StationState stationState = originalTrain.stationState();
            com.simibubi.create.content.trains.station.GlobalStation station = originalTrain.currentStation();
            originalTrain.relocate(rebuilt.carriage(), rebuilt.bogeys());
            for (BlockPos pos : offTrackLocal) {
                originalTrain.markBogeyOffTrack(pos);
            }
            if (!offTrackLocal.isEmpty() && offTrackLocal.size() >= rebuilt.bogeys().size()) {
                originalTrain.setDerailedQuiet(true);
            }
            if (keepMotion) {
                originalTrain.setSpeed(speed);
                originalTrain.setTargetSpeed(targetSpeed);
                originalTrain.setStationState(stationState);
                originalTrain.setCurrentStation(station);
            }
            settleAfterSplit(originalTrain);
            SableTrainPersistence.persist(originalTrain);
            SableTrainSyncPacket.broadcast(originalTrain);
        } else if (scanBogeyPlotPositions(sub.getPlot()).isEmpty()) {
            SubLevelMergeWatcher.dropTrain(level, originalTrain);
        } else if (!originalTrain.isDerailed()) {
            originalTrain.setDerailed(true);
            SableTrainPersistence.persist(originalTrain);
            SableTrainSyncPacket.broadcast(originalTrain);
        }
    }

    private static final double DONOR_MATCH_RADIUS_SQ = 9.0;

    private static final double ADOPT_MATCH_RADIUS_SQ = 0.05;

    private static final double INLINE_TOLERANCE = 0.6;

    private static final Map<UUID, Set<BlockPos>> IGNORED_BOGEYS = new HashMap<>();

    private static final Map<UUID, Long> IGNORED_RETRY_AT = new HashMap<>();

    private static final long IGNORED_RETRY_TICKS = 40L;

    static int ignoredCount(UUID trainId) {
        Set<BlockPos> ignored = IGNORED_BOGEYS.get(trainId);
        return ignored == null ? 0 : ignored.size();
    }

    static SableTrain.Car buildCarFromSubLevelScan(ServerLevel level, ServerSubLevel sub, Vec3 railDir,
                                                   List<SableTrain.Bogey> donors, RailCarriage reference,
                                                   SableTrain originalTrain, ServerSubLevel origSub,
                                                   List<BlockPos> offTrackLocal) {
        return buildCarFromSubLevelScan(level, sub, railDir, donors, reference, originalTrain, origSub,
                offTrackLocal, DONOR_MATCH_RADIUS_SQ, null, false);
    }

    // a merge keeps every bogey of both trains, the per carriage cap is for assembly and would drop the middle pair of a 2 plus 2
    static SableTrain.Car buildMergedCar(ServerLevel level, ServerSubLevel sub, Vec3 railDir,
                                         List<SableTrain.Bogey> donors, RailCarriage reference) {
        return buildCarFromSubLevelScan(level, sub, railDir, donors, reference, null, null, new ArrayList<>(),
                DONOR_MATCH_RADIUS_SQ, null, true);
    }

    private record Adoption(List<SableTrain.Bogey> seatFrom, Set<BlockPos> ignore) {
    }

    private record BogeySeat(RailCarriage rail, double lateral, double along, SableTrain.Bogey from,
                             boolean independent) {
    }

    private static BogeySeat seatAdopted(ServerLevel level, ServerSubLevel sub, List<SableTrain.Bogey> seatFrom,
                                         Vector3d bogeyWorld, Vec3 railDir) {
        if (seatFrom.isEmpty()) {
            return null;
        }
        BogeySeat own = seatOnOwnRail(seatFrom, bogeyWorld, sub);
        if (own != null && own.lateral() <= INLINE_TOLERANCE) {
            return own;
        }
        SableTrainSpawner.TrackHit hit = SableTrainSpawner.findRailBelow(level,
                BlockPos.containing(bogeyWorld.x, bogeyWorld.y, bogeyWorld.z), railDir);
        RailCarriage rail = hit == null ? null : RailCarriage.at(hit.location(), hit.upNormal(), 1.0, 0.0);
        if (rail == null) {
            return null;
        }
        double along = alignToBogey(rail, bogeyWorld);
        rail.setIndependent(true);
        return new BogeySeat(rail, own == null ? Double.MAX_VALUE : own.lateral(), along,
                own == null ? seatFrom.get(0) : own.from(), true);
    }

    private static BogeySeat seatOnOwnRail(List<SableTrain.Bogey> seatFrom, Vector3d bogeyWorld, ServerSubLevel sub) {
        SableTrain.Bogey nearest = null;
        double best = Double.MAX_VALUE;
        for (SableTrain.Bogey candidate : seatFrom) {
            double distSqr = SubLevelData.worldOfOffset(sub, candidate.localPos()).distanceSquared(bogeyWorld);
            if (distSqr < best) {
                best = distSqr;
                nearest = candidate;
            }
        }
        if (nearest == null || nearest.rail().seedPoint() == null) {
            return null;
        }
        RailCarriage rail = RailCarriage.seededAt(nearest.rail().seedPoint(), nearest.rail().graph(),
                nearest.rail().upNormal(), 1.0, 0.0);
        if (rail == null) {
            return null;
        }
        rail.setPortal(nearest.rail().portal());
        double along = alignToBogey(rail, bogeyWorld);
        return new BogeySeat(rail, lateralMiss(rail, bogeyWorld), along, nearest, false);
    }

    private static boolean floatsAboveRail(RailCarriage rail, Vector3d bogeyWorld) {
        Vector3d at = rail == null ? null : rail.center();
        if (at == null) {
            return false;
        }
        Vec3 up = rail.upNormal();
        double gap = (bogeyWorld.x - at.x) * up.x + (bogeyWorld.y - at.y) * up.y + (bogeyWorld.z - at.z) * up.z;
        return gap > MAX_BOGEY_RAIL_GAP;
    }

    private static final double MAX_BOGEY_RAIL_GAP = 1.0;

    private static final double MERGE_OFF_CHAIN_LATERAL = 1.5;

    private static double lateralMiss(RailCarriage rail, Vector3d bogeyWorld) {
        Vector3d at = rail.center();
        Vec3 dir = rail.forward();
        double flat = dir == null ? 0.0 : Math.hypot(dir.x, dir.z);
        if (at == null || flat < 1.0e-6) {
            return Double.MAX_VALUE;
        }
        return Math.abs((bogeyWorld.x - at.x) * dir.z - (bogeyWorld.z - at.z) * dir.x) / flat;
    }

    private static double alignToBogey(RailCarriage rail, Vector3d bogeyWorld) {
        Vector3d c = rail.center();
        Vec3 fwd = rail.forward();
        if (c == null) {
            return 0.0;
        }
        double ds = (bogeyWorld.x - c.x) * fwd.x + (bogeyWorld.z - c.z) * fwd.z;
        if (Math.abs(ds) > 1.0e-3) {
            rail.setSpeed(ds);
            rail.tick();
            rail.setSpeed(0.0);
        }
        return ds;
    }

    private static SableTrain.Car buildCarFromSubLevelScan(ServerLevel level, ServerSubLevel sub, Vec3 railDir,
                                                           List<SableTrain.Bogey> donors, RailCarriage reference,
                                                           SableTrain originalTrain, ServerSubLevel origSub,
                                                           List<BlockPos> offTrackLocal, double donorRadiusSq,
                                                           Adoption adoption, boolean merge) {
        List<SableTrain.Bogey> donorsBefore = new ArrayList<>(donors);
        Set<BlockPos> matched = new HashSet<>();
        Set<BlockPos> floating = new HashSet<>();
        SableTrain.Car car = scanCar(level, sub, railDir, donors, reference, originalTrain, origSub, offTrackLocal,
                donorRadiusSq, adoption, Set.of(), matched, floating, false, merge);
        if (car == null && !floating.isEmpty() && originalTrain != null && adoption == null) {
            offTrackLocal.clear();
            donors.clear();
            donors.addAll(donorsBefore);
            return scanCar(level, sub, railDir, donors, reference, originalTrain, origSub, offTrackLocal,
                    donorRadiusSq, adoption, Set.of(), new HashSet<>(), new HashSet<>(), true, merge);
        }
        int max = merge ? Integer.MAX_VALUE : LoconauticsConfig.MAX_BOGEYS_PER_CARRIAGE.get();
        if (car == null || car.bogeys().size() <= max) {
            return car;
        }
        List<BlockPos> along = car.bogeys().stream().map(SableTrain.Bogey::localPos).toList();
        Set<BlockPos> order = new LinkedHashSet<>();
        order.add(along.get(0));
        order.add(along.get(along.size() - 1));
        along.stream().filter(matched::contains).forEach(order::add);
        order.addAll(along);
        List<BlockPos> ranked = new ArrayList<>(order);
        Set<BlockPos> skip = new HashSet<>(ranked.subList(max, ranked.size()));
        offTrackLocal.clear();
        donors.clear();
        donors.addAll(donorsBefore);
        return scanCar(level, sub, railDir, donors, reference, originalTrain, origSub, offTrackLocal, donorRadiusSq,
                adoption, skip, new HashSet<>(), new HashSet<>(), false, merge);
    }

    private static SableTrain.Car scanCar(ServerLevel level, ServerSubLevel sub, Vec3 railDir,
                                          List<SableTrain.Bogey> donors, RailCarriage reference,
                                          SableTrain originalTrain, ServerSubLevel origSub,
                                          List<BlockPos> offTrackLocal, double donorRadiusSq, Adoption adoption,
                                          Set<BlockPos> skip, Set<BlockPos> matched, Set<BlockPos> floating,
                                          boolean keepFloating, boolean merge) {
        ServerLevelPlot plot = sub.getPlot();
        BlockPos plotCenter = plot.getCenterBlock();
        List<BlockPos> bogeyPlotPositions = scanBogeyPlotPositions(plot);
        if (bogeyPlotPositions.isEmpty()) return null;
        Vec3 sortDir = referenceSortDirection(reference, railDir);
        bogeyPlotPositions.sort(
                java.util.Comparator.comparingDouble(p -> p.getX() * sortDir.x + p.getZ() * sortDir.z));

        List<SableTrain.Bogey> bogeys = new ArrayList<>();
        List<Vector3d> bogeyWorlds = new ArrayList<>();
        RailCarriage seedRail = null;
        Vector3d seedWorld = null;
        boolean seededOffTrack = false;
        for (BlockPos plotPos : bogeyPlotPositions) {
            if (adoption != null && adoption.ignore().contains(plotPos)) continue;
            if (skip.contains(plotPos.subtract(plotCenter))) continue;
            Vector3d bogeyWorld = sub.logicalPose().transformPosition(
                    new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5),
                    new Vector3d());
            SableTrain.Bogey donor = takeDonorBogey(donors, bogeyWorld, origSub, donorRadiusSq);
            RailCarriage bogeyRail = donor == null ? null : donor.rail();
            // a wholly derailed car never flagged its bogeys one by one, every bogey it hands over is off the rail
            boolean wasOffTrack = donor != null && originalTrain != null
                    && (originalTrain.isDerailed() || originalTrain.isBogeyOffTrack(donor.localPos()));
            if (bogeyRail == null && adoption != null) {
                BogeySeat seat = seatAdopted(level, sub, adoption.seatFrom(), bogeyWorld, railDir);
                if (seat == null) continue;
                bogeyRail = seat.rail();
            } else if (bogeyRail == null) {
                SableTrainSpawner.TrackHit hit = SableTrainSpawner.findRailBelow(level,
                        BlockPos.containing(bogeyWorld.x, bogeyWorld.y, bogeyWorld.z), railDir);
                if (hit == null) continue;
                bogeyRail = RailCarriage.at(hit.location(), hit.upNormal(), 1.0, 0.0);
                if (bogeyRail == null) continue;
                alignToBogey(bogeyRail, bogeyWorld);
            }
            if (merge && donor != null) {
                // the glue pull moved the blocks after the rail was seated, so slide the rail to where the bogey really is
                alignToBogey(bogeyRail, bogeyWorld);
                if (lateralMiss(bogeyRail, bogeyWorld) > MERGE_OFF_CHAIN_LATERAL) {
                    bogeyRail.setIndependent(true);
                }
            }
            boolean floats = !wasOffTrack && floatsAboveRail(bogeyRail, bogeyWorld);
            if (floats && !keepFloating) {
                floating.add(plotPos.subtract(plotCenter));
                continue;
            }
            wasOffTrack |= floats;
            if (donor != null) {
                matched.add(plotPos.subtract(plotCenter));
            }
            boolean onChain = !bogeyRail.isIndependent();
            if (onChain && (seedRail == null || (seededOffTrack && !wasOffTrack))) {
                seedRail = bogeyRail;
                seedWorld = bogeyWorld;
                seededOffTrack = wasOffTrack;
            }
            BlockPos newLocal = plotPos.subtract(plotCenter);
            bogeys.add(new SableTrain.Bogey(newLocal, bogeyRail));
            if (onChain) {
                bogeyWorlds.add(bogeyWorld);
            }
            if (wasOffTrack) {
                offTrackLocal.add(newLocal);
            }
        }
        if (bogeys.isEmpty() || seedRail == null) return null;

        double spacing = 1.0;
        Vector3d firstBogey = bogeyWorlds.get(0);
        Vector3d lastBogey = bogeyWorlds.get(bogeyWorlds.size() - 1);
        if (bogeyWorlds.size() >= 2) {
            spacing = Math.max(1.0, new Vector3d(lastBogey).sub(firstBogey).length());
        }
        RailCarriage carriage = RailCarriage.seededAt(seedRail.seedPoint(), seedRail.graph(),
                seedRail.upNormal(), spacing, 0.0);
        if (carriage == null) return null;
        carriage.setPortal(seedRail.portal());
        if (bogeyWorlds.size() >= 2) {
            Vec3 seedFwd = carriage.forward();
            Vector3d mid = new Vector3d(firstBogey).add(lastBogey).mul(0.5);
            double toward = (mid.x - seedWorld.x) * seedFwd.x + (mid.z - seedWorld.z) * seedFwd.z;
            if (Math.abs(toward) > 1.0e-3) {
                carriage.unstick();
                carriage.setSpeed(toward);
                carriage.tick();
                carriage.setSpeed(0.0);
            }
        }
        carriage.adoptReferenceFrame(reference);

        Vec3 leadW = carriage.leadingPos();
        Vec3 trailW = carriage.trailingPos();
        Vector3dc localLead = null, localTrail = null;
        if (leadW != null && trailW != null) {
            Vec3 ll = sub.logicalPose().transformPositionInverse(leadW);
            Vec3 lt = sub.logicalPose().transformPositionInverse(trailW);
            localLead = new Vector3d(ll.x, ll.y, ll.z);
            localTrail = new Vector3d(lt.x, lt.y, lt.z);
        }
        return new SableTrain.Car(sub.getUniqueId(), carriage, bogeys, localLead, localTrail);
    }

    // the frame the blocks were laid out in, which is what creates rotation anchors are keyed to and is the only thing that never flips
    static Vec3 referenceSortDirection(RailCarriage reference, Vec3 railDir) {
        Vector3d frame = reference == null ? null : reference.referenceForward();
        if (frame == null || frame.lengthSquared() < 1.0e-9) {
            return railDir;
        }
        return new Vec3(frame.x, frame.y, frame.z);
    }

    // nearest donors own block position, never its rail follower, which drifts the moment a bogey derails
    static SableTrain.Bogey takeDonorBogey(List<SableTrain.Bogey> donors, Vector3d bogeyWorld, ServerSubLevel origSub,
                                           double radiusSq) {
        if (donors == null || donors.isEmpty()) {
            return null;
        }
        int bestIndex = -1;
        double bestDistSqr = radiusSq;
        for (int i = 0; i < donors.size(); i++) {
            SableTrain.Bogey candidate = donors.get(i);
            Vector3d position = origSub != null
                    ? SubLevelData.worldOfOffset(origSub, candidate.localPos())
                    : (candidate.rail() == null ? null : candidate.rail().center());
            if (position == null) {
                continue;
            }
            double distSqr = position.distanceSquared(bogeyWorld);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                bestIndex = i;
            }
        }
        return bestIndex < 0 ? null : donors.remove(bestIndex);
    }

    static void clearOrphanPads(ServerSubLevel sub) {
        ServerLevel subLevel = sub.getLevel();
        List<BlockPos> orphans = scanPlotBlocks(sub.getPlot(), WheelColliders::isPad);
        orphans.removeIf(pos -> !WheelColliders.isOrphanPad(subLevel, pos));
        for (BlockPos pos : orphans) {
            subLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    public static List<BlockPos> scanBogeyPlotPositions(ServerLevelPlot plot) {
        return scanPlotBlocks(plot, state -> state.getBlock() instanceof AbstractBogeyBlock);
    }

    private static List<BlockPos> scanPlotBlocks(ServerLevelPlot plot, java.util.function.Predicate<BlockState> predicate) {
        List<BlockPos> out = new ArrayList<>();
        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            LevelChunkSection[] sections = chunk.getSections();
            for (int si = 0; si < chunk.getSectionsCount(); si++) {
                LevelChunkSection section = sections[si];
                if (section == null || section.hasOnlyAir()) continue;
                int sectionY = chunk.getSectionYFromSectionIndex(si);
                for (int bx = 0; bx < 16; bx++) {
                    for (int by = 0; by < 16; by++) {
                        for (int bz = 0; bz < 16; bz++) {
                            if (!predicate.test(section.getBlockState(bx, by, bz))) {
                                continue;
                            }
                            out.add(new BlockPos((chunk.getPos().x << 4) + bx,
                                    (sectionY << 4) + by, (chunk.getPos().z << 4) + bz));
                        }
                    }
                }
            }
        }
        return out;
    }

    public static void ensureObserver(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || OBSERVED.contains(container)) {
            return;
        }
        container.addObserver(new SubLevelObserver() {

            @Override
            public void onSubLevelAdded(SubLevel subLevel) {
                com.lycoris.loconautics.common.sable.TrackHostFrameSync.onLoaded(subLevel.getUniqueId());
                com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.noteSubLevelAdded(subLevel);
                if (com.lycoris.loconautics.trainphysics.portal.TrainPortalCrossing.isCrossing(
                        subLevel.getUniqueId())) {
                    return;
                }
                com.lycoris.loconautics.trainphysics.state.TrainResidency.onSubLevelAdded(subLevel);
            }

            @Override
            public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                boolean outer = inRemovalCallback;
                inRemovalCallback = true;
                try {
                    subLevelRemoved(subLevel, reason);
                } finally {
                    inRemovalCallback = outer;
                }
            }
        });
        OBSERVED.add(container);
    }

    private static void subLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        // unloaded means the sub level will come back, so only removed drops the cart, or every shutdown would wipe persistence
        if (reason != SubLevelRemovalReason.REMOVED) {
            if (reason == SubLevelRemovalReason.UNLOADED) {
                if (subLevel instanceof ServerSubLevel server) {
                    com.lycoris.loconautics.common.sable.TrackHostFrameSync.onUnloaded(server,
                            subLevel.getLevel().dimension());
                }
                com.lycoris.loconautics.trainphysics.state.TrainResidency.onSubLevelUnloaded(subLevel);
            }
            return;
        }
        UUID removedId = subLevel.getUniqueId();
        if (com.lycoris.loconautics.trainphysics.portal.TrainPortalCrossing.isCrossing(removedId)) {
            return;
        }
        com.lycoris.loconautics.common.sable.TrackHostFrameSync.onRemoved(removedId);
        for (SableTrain train : SableTrainRegistry.all()) {
            if (removedId.equals(train.car().subLevelId())) {
                com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.releaseAll(train);
                SableTrainRegistry.remove(train.id());
                RerailApproach.forget(train.id());
                SableTrainPersistence.unpersist(train.level().getServer(), train.id());
                SableTrainSyncPacket.broadcastRemoval(removedId);
            }
        }
    }

    private SubLevelSplitWatcher() {
    }
}
