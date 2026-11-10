package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import net.minecraft.core.BlockPos;

import java.util.UUID;


public final class TrainSyncCache {
    private TrainSyncCache() {
    }

    static final java.util.Map<UUID, Double> SYNCED_SPEED = new java.util.HashMap<>();
    static final java.util.Map<UUID, Double> SYNCED_WHEELDRIVE = new java.util.HashMap<>();
    static final java.util.Set<UUID> SYNCED_DERAILED = new java.util.HashSet<>();
    static final java.util.Set<UUID> SYNCED_BRAKED = new java.util.HashSet<>();
    static final java.util.Map<UUID, Integer> SYNCED_OFFTRACK = new java.util.HashMap<>();
    // coupling a standing rake changes nothing else here, so without this the relocator ghost keeps the old length
    static final java.util.Map<UUID, Double> SYNCED_CONSIST = new java.util.HashMap<>();
    static final java.util.Map<UUID, java.util.Map<BlockPos, Double>> SYNCED_DRIVEN = new java.util.HashMap<>();
    static final java.util.Map<UUID, java.util.Set<BlockPos>> SYNCED_SLIPPING = new java.util.HashMap<>();
    static final java.util.Map<UUID, Double> SYNCED_SLIP = new java.util.HashMap<>();
    static final java.util.Map<UUID, Double> SYNCED_TOP = new java.util.HashMap<>();

    static void retain(java.util.Set<UUID> live) {
        SYNCED_SPEED.keySet().retainAll(live);
        SYNCED_WHEELDRIVE.keySet().retainAll(live);
        SYNCED_DERAILED.retainAll(live);
        SYNCED_BRAKED.retainAll(live);
        SYNCED_OFFTRACK.keySet().retainAll(live);
        SYNCED_CONSIST.keySet().retainAll(live);
        SYNCED_DRIVEN.keySet().retainAll(live);
        SYNCED_SLIPPING.keySet().retainAll(live);
        SYNCED_SLIP.keySet().retainAll(live);
        SYNCED_TOP.keySet().retainAll(live);
    }

    public static double syncedConsistLength(SableTrain train) {
        Double known = SYNCED_CONSIST.get(train.id());
        return known != null ? known
                : com.lycoris.loconautics.trainphysics.TrainLength.consistLength(train.level(), train.car().subLevelId());
    }

    static void sync(SableTrain train) {
        double speed = train.speed();
        double wheelDrive = train.wheelDrive();
        boolean derailed = train.isDerailed();
        boolean braked = train.brakeHard();
        int offTrack = train.offTrackBogeyCount();
        Double last = SYNCED_SPEED.get(train.id());
        Double lastWheel = SYNCED_WHEELDRIVE.get(train.id());
        boolean lastDerailed = SYNCED_DERAILED.contains(train.id());
        boolean lastBraked = SYNCED_BRAKED.contains(train.id());
        Integer lastOffTrack = SYNCED_OFFTRACK.get(train.id());
        java.util.Map<BlockPos, Double> driven = train.drivenBogeys();
        java.util.Set<BlockPos> slipping = train.slippingBogeys();
        double slip = train.slipFactor();
        Double lastSlip = SYNCED_SLIP.get(train.id());
        double top = TrainPropulsion.gearedTopBpt(train);
        Double lastTop = SYNCED_TOP.get(train.id());
        Double lastConsist = SYNCED_CONSIST.get(train.id());
        double consist = lastConsist == null || train.level().getGameTime() % 20 == 0
                ? com.lycoris.loconautics.trainphysics.TrainLength.consistLength(train.level(), train.car().subLevelId())
                : lastConsist;
        if (last != null && Math.abs(last - speed) < 0.004 && !settledToZero(last, speed)
                && lastWheel != null && Math.abs(lastWheel - wheelDrive) < 0.004 && !settledToZero(lastWheel, wheelDrive)
                && derailed == lastDerailed && braked == lastBraked
                && lastOffTrack != null && lastOffTrack == offTrack
                && lastSlip != null && Math.abs(lastSlip - slip) < 0.01
                && lastTop != null && Math.abs(lastTop - top) <= lastTop * 0.01
                && lastConsist != null && Math.abs(lastConsist - consist) < 0.01
                && slipping.equals(SYNCED_SLIPPING.getOrDefault(train.id(), java.util.Set.of()))
                && sameDriven(SYNCED_DRIVEN.get(train.id()), driven)) {
            return;
        }
        SYNCED_DRIVEN.put(train.id(), new java.util.LinkedHashMap<>(driven));
        SYNCED_SLIPPING.put(train.id(), java.util.Set.copyOf(slipping));
        SYNCED_SLIP.put(train.id(), slip);
        SYNCED_TOP.put(train.id(), top);
        SYNCED_SPEED.put(train.id(), speed);
        SYNCED_WHEELDRIVE.put(train.id(), wheelDrive);
        SYNCED_OFFTRACK.put(train.id(), offTrack);
        SYNCED_CONSIST.put(train.id(), consist);
        if (derailed) {
            SYNCED_DERAILED.add(train.id());
        } else {
            SYNCED_DERAILED.remove(train.id());
        }
        if (braked) {
            SYNCED_BRAKED.add(train.id());
        } else {
            SYNCED_BRAKED.remove(train.id());
        }
        SableTrainSyncPacket.broadcast(train);
    }

    private static boolean settledToZero(double last, double now) {
        return Math.abs(now) < 1.0e-3 && last != now;
    }

    private static boolean sameDriven(java.util.Map<BlockPos, Double> last, java.util.Map<BlockPos, Double> now) {
        if (last == null || last.size() != now.size()) {
            return false;
        }
        for (java.util.Map.Entry<BlockPos, Double> entry : now.entrySet()) {
            Double previous = last.get(entry.getKey());
            if (previous == null || Math.abs(previous - entry.getValue()) >= 0.004 || settledToZero(previous, entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
