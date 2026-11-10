package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CcNetworkEvents {

    private static final int SCAN_INTERVAL = 4;

    private static final Map<String, String> SIGNAL_STATES = new HashMap<>();
    private static final Map<UUID, Set<UUID>> BLOCK_TRAINS = new HashMap<>();
    private static final Set<UUID> KNOWN_TRAINS = new HashSet<>();
    private static int counter;
    private static boolean primed;

    private CcNetworkEvents() {
    }

    public static void tick() {
        if (!CcEvents.anyWatchesNetwork()) {
            SIGNAL_STATES.clear();
            BLOCK_TRAINS.clear();
            KNOWN_TRAINS.clear();
            primed = false;
            return;
        }
        if (counter++ % SCAN_INTERVAL != 0 || Create.RAILWAYS == null) {
            return;
        }
        CcNetwork.Fleet fleet = CcNetwork.fleet();
        scanTrains(fleet);
        scanBlocks(fleet);
        scanSignals();
        primed = true;
    }

    private static void scanTrains(CcNetwork.Fleet fleet) {
        Set<UUID> now = new HashSet<>();
        for (SableTrain train : fleet.trains()) {
            now.add(train.id());
            if (KNOWN_TRAINS.add(train.id()) && primed) {
                CcEvents.broadcast("train_added", train.id().toString());
            }
        }
        for (UUID gone : new ArrayList<>(KNOWN_TRAINS)) {
            if (!now.contains(gone)) {
                KNOWN_TRAINS.remove(gone);
                CcEvents.broadcast("train_removed", gone.toString());
            }
        }
    }

    private static void scanBlocks(CcNetwork.Fleet fleet) {
        Set<UUID> seen = new HashSet<>();
        for (SignalEdgeGroup group : new ArrayList<>(Create.RAILWAYS.signalEdgeGroups.values())) {
            Set<UUID> now = new HashSet<>();
            for (UUID car : TrainSignals.occupantsOf(group.id)) {
                UUID train = fleet.ownerOfCar().get(car);
                if (train != null) {
                    now.add(train);
                }
            }
            Set<UUID> before = BLOCK_TRAINS.getOrDefault(group.id, Set.of());
            for (UUID train : now) {
                if (primed && !before.contains(train)) {
                    CcEvents.broadcast("block_occupied", group.id.toString(), train.toString());
                }
            }
            for (UUID train : before) {
                if (!now.contains(train)) {
                    CcEvents.broadcast("block_cleared", group.id.toString(), train.toString());
                }
            }
            if (now.isEmpty()) {
                BLOCK_TRAINS.remove(group.id);
            } else {
                BLOCK_TRAINS.put(group.id, now);
                seen.add(group.id);
            }
        }
        BLOCK_TRAINS.keySet().retainAll(seen);
    }

    private static void scanSignals() {
        Set<String> seen = new HashSet<>();
        for (TrackGraph graph : new ArrayList<>(Create.RAILWAYS.trackNetworks.values())) {
            for (SignalBoundary signal : graph.getPoints(EdgePointType.SIGNAL)) {
                for (boolean primary : new boolean[] { true, false }) {
                    if (signal.blockEntities.get(primary).isEmpty()) {
                        continue;
                    }
                    String key = signal.getId() + (primary ? "#1" : "#2");
                    String state = CcNetwork.stateOf(signal, primary);
                    seen.add(key);
                    String before = SIGNAL_STATES.put(key, state);
                    if (before != null && !before.equals(state)) {
                        CcEvents.broadcast("signal_changed", signal.getId().toString(), primary ? 1 : 2, state, before);
                    }
                }
            }
        }
        SIGNAL_STATES.keySet().retainAll(seen);
    }
}
