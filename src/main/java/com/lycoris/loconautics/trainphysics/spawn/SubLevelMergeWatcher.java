package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SubLevelMergeWatcher {

    private static final long PULL_TICKS = 16;
    private static final Map<UUID, Pull> PULLING = new ConcurrentHashMap<>();

    private record Pull(UUID host, long until) {}

    private SubLevelMergeWatcher() {
    }

    public static void onMerged(ServerLevel level, UUID goneSubId, UUID hostSubId) {
        if (level == null || goneSubId == null || hostSubId == null || goneSubId.equals(hostSubId)) {
            return;
        }
        PULLING.remove(goneSubId);
        PULLING.remove(hostSubId);
        SableTrain gone = SableTrainRegistry.bySubLevel(goneSubId);
        SableTrain host = SableTrainRegistry.bySubLevel(hostSubId);
        if (gone == null && host == null) {
            return;
        }
        if (host == null) {
            adoptIntoPlainBody(level, gone, hostSubId);
            return;
        }

        ServerSubLevel sub = SubLevelData.resolve(level, hostSubId);
        if (sub == null) {
            return;
        }
        // a plain piece with no bogey only adds weight, so the car stays glued and is not seated again
        if (gone == null && !host.isTrackless()
                && SubLevelSplitWatcher.scanBogeyPlotPositions(sub.getPlot()).size() <= host.car().bogeys().size()) {
            releaseStaffLock(level, sub);
            return;
        }

        List<SableTrain.Bogey> donors = new ArrayList<>(host.car().bogeys());
        if (gone != null) {
            donors.addAll(gone.car().bogeys());
        }
        SubLevelSplitWatcher.clearOrphanPads(sub);
        releaseStaffLock(level, sub);
        if (gone != null) {
            carryIdentity(gone, host);
        }
        if (host.isTrackless()) {
            SableTrain.Car rebuilt = TracklessCars.build(sub);
            if (rebuilt != null) {
                host.replaceCar(rebuilt);
                SubLevelSplitWatcher.settleAfterSplit(host);
                SableTrainPersistence.persist(host);
                SableTrainSyncPacket.broadcast(host);
            }
            dropTrain(level, gone);
            return;
        }
        Vec3 railDir = host.car().carriage().forward();
        SableTrain.Car rebuilt = SubLevelSplitWatcher.buildMergedCar(level, sub, railDir, donors,
                host.car().carriage());

        if (rebuilt != null && !rebuilt.bogeys().isEmpty()) {
            double speed = host.speed();
            double target = host.targetSpeed();
            host.relocate(rebuilt);
            host.setSpeed(speed);
            host.setTargetSpeed(target);
            SubLevelSplitWatcher.settleAfterSplit(host);
            SableTrainPersistence.persist(host);
            SableTrainSyncPacket.broadcast(host);
        }

        dropTrain(level, gone);
    }

    private static void adoptIntoPlainBody(ServerLevel level, SableTrain gone, UUID hostSubId) {
        ServerSubLevel sub = SubLevelData.resolve(level, hostSubId);
        if (sub == null) {
            dropTrain(level, gone);
            return;
        }
        SubLevelSplitWatcher.clearOrphanPads(sub);
        releaseStaffLock(level, sub);
        boolean trackless = gone.isTrackless();
        SableTrain.Car rebuilt = trackless
                ? TracklessCars.build(sub)
                : SubLevelSplitWatcher.buildMergedCar(level, sub, gone.car().carriage().forward(),
                        new ArrayList<>(gone.car().bogeys()), gone.car().carriage());
        dropTrain(level, gone);
        if (rebuilt == null || rebuilt.bogeys().isEmpty()) {
            return;
        }
        SableTrain adopted = new SableTrain(UUID.randomUUID(), level, rebuilt, gone.isPhysics());
        adopted.setOwner(gone.owner());
        adopted.setTargetSpeed(0.0);
        carryIdentity(gone, adopted);
        if (trackless) {
            adopted.setDerailedQuiet(true);
        }
        SableTrainRegistry.register(adopted);
        SubLevelSplitWatcher.settleAfterSplit(adopted);
        SableTrainPersistence.persist(adopted);
        SableTrainSyncPacket.broadcast(adopted);
    }

    private static void carryIdentity(SableTrain from, SableTrain to) {
        if (from.isPowered() && !to.isPowered()) {
            to.setPowered(true);
        }
        if (from.isScheduleDriven() && !to.isScheduleDriven()) {
            to.setScheduleDriven(true);
        }
        if (to.name().isEmpty() && !from.name().isEmpty()) {
            to.setName(from.name());
        }
        if (to.iconType().isEmpty() && !from.iconType().isEmpty()) {
            to.setIconType(from.iconType());
        }
        if (to.owner() == null && from.owner() != null) {
            to.setOwner(from.owner());
        }
    }

    public static void beginPull(ServerLevel level, UUID goneSubId, UUID hostSubId) {
        PULLING.put(goneSubId, new Pull(hostSubId, level.getGameTime() + PULL_TICKS));
    }

    public static boolean pulling(ServerLevel level, UUID subId) {
        Pull pull = PULLING.get(subId);
        if (pull == null) {
            return false;
        }
        if (level.getGameTime() > pull.until()) {
            PULLING.remove(subId);
            return false;
        }
        return true;
    }

    // the body being pulled and the body it merges into, both are shoved by the pull and neither is a crash
    public static boolean merging(ServerLevel level, UUID subId) {
        if (pulling(level, subId)) {
            return true;
        }
        for (Map.Entry<UUID, Pull> entry : PULLING.entrySet()) {
            if (entry.getValue().host().equals(subId) && pulling(level, entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    public static boolean mergingPair(ServerLevel level, UUID a, UUID b) {
        Pull pullA = PULLING.get(a);
        Pull pullB = PULLING.get(b);
        return pullA != null && pullA.host().equals(b) && pulling(level, a)
                || pullB != null && pullB.host().equals(a) && pulling(level, b);
    }

    public static void releaseStaffLock(ServerLevel level, SubLevel sub) {
        PhysicsStaffServerHandler staff = PhysicsStaffServerHandler.get(level);
        if (sub == null || !staff.isLocked(sub)) {
            return;
        }
        staff.removeLock(sub);
    }

    static void dropTrain(ServerLevel level, SableTrain gone) {
        if (gone == null) {
            return;
        }
        GhostWheelBodies.releaseAll(gone);
        SableTrainRegistry.remove(gone.id());
        RerailApproach.forget(gone.id());
        if (level.getServer() != null) {
            SableTrainPersistence.unpersist(level.getServer(), gone.id());
        }
        UUID subId = gone.car() == null ? null : gone.car().subLevelId();
        if (subId != null) {
            SableTrainSyncPacket.broadcastRemoval(subId);
        }
    }
}
