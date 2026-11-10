package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.common.BogeyGroundDrop;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AssembledBogeyTrains {

    private record Pending(ServerLevel level, UUID subLevelId) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private AssembledBogeyTrains() {
    }

    public static void queue(ServerLevel level, ServerSubLevel sub) {
        synchronized (PENDING) {
            PENDING.add(new Pending(level, sub.getUniqueId()));
        }
    }

    public static void processPending() {
        List<Pending> batch;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            batch = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Pending pending : batch) {
            ServerSubLevel sub = SubLevelData.resolve(pending.level(), pending.subLevelId());
            if (sub == null || isSpokenFor(sub)) continue;
            List<BlockPos> bogeys = SubLevelSplitWatcher.scanBogeyPlotPositions(sub.getPlot());
            if (bogeys.isEmpty()) continue;
            if (toTrain(pending.level(), sub, bogeys.get(0))) {
                liftGroundDrop(sub, bogeys);
            }
        }
    }

    private static boolean isSpokenFor(ServerSubLevel sub) {
        UUID id = sub.getUniqueId();
        return SableTrainRegistry.bySubLevel(id) != null || GhostWheelBodies.isWheelBody(id) || PortalTwins.isTwin(sub);
    }

    private static void liftGroundDrop(ServerSubLevel sub, List<BlockPos> bogeys) {
        for (BlockPos pos : bogeys) {
            if (sub.getLevel().getBlockEntity(pos) instanceof AbstractBogeyBlockEntity bogey) {
                BogeyGroundDrop.set(bogey, false);
            }
        }
    }

    private static boolean toTrain(ServerLevel level, ServerSubLevel sub, BlockPos firstBogey) {
        SableTrain.Car car = SubLevelSplitWatcher.buildCarFromSubLevelScan(level, sub,
                TracklessCars.railDirection(sub, firstBogey), new ArrayList<>(), null, null, null, new ArrayList<>());
        boolean railed = car != null;
        if (!railed) {
            car = TracklessCars.build(sub);
        }
        if (car == null) {
            return false;
        }
        SableTrain train = new SableTrain(UUID.randomUUID(), level, car, false);
        if (!railed) {
            train.setDerailedQuiet(true);
        }
        SableTrainRegistry.register(train);
        SableTrainSpawner.nameCar(level, car, SableTrainSpawner.defaultTrainName());
        SubLevelSplitWatcher.ensureObserver(level);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
        return true;
    }
}
