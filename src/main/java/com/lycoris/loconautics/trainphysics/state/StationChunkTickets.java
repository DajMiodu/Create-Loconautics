package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.StationState;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;



public final class StationChunkTickets {

    public static final int RADIUS_CHUNKS = 1;

    // a train held forever by a condition that never comes true must not pin the world for the rest of its life
    public static final int MAX_DWELL_TICKS = 6000;

    private static final Map<UUID, Held> HELD = new HashMap<>();

    // a train that hit the dwell cap must not force the same square again next tick, it stays capped until it leaves
    private static final Set<UUID> CAPPED = new HashSet<>();



    private static TicketController controller;

    private record Held(ServerLevel level, ChunkPos center, long sinceTick, Set<ChunkPos> chunks) {}

    private record ChunkKey(ServerLevel level, long chunk) {}

    private StationChunkTickets() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        controller = new TicketController(LoconauticsConstants.id("station_dwell"),
                (level, helper) -> {
                    for (UUID owner : helper.getEntityTickets().keySet()) {
                        helper.removeAllTickets(owner);
                    }
                });
        event.register(controller);
    }

    public static void update(SableTrain train) {
        BlockPos ghostHold = TrainResidency.ghostHoldAnchor(train);
        BlockPos anchor = ghostHold != null ? ghostHold : stationAnchorIfEnabled(train);
        if (anchor == null) {
            CAPPED.remove(train.id());
            if (holdChainAfterEmbody(train)) {
                return;
            }
            release(train.id());
            return;
        }
        if (train.isGhost() && TrainResidency.pullBlocked(train)) {
            release(train.id());
            return;
        }
        if (ghostHold != null) {
            boolean lost = TrainResidency.bodyLost(train);
            if (!lost) {
                CAPPED.remove(train.id());
            } else if (CAPPED.contains(train.id())) {
                return;
            }
            acquire(train, new ChunkPos(anchor), lost);
            return;
        }
        if (CAPPED.contains(train.id())) {
            return;
        }
        acquire(train, new ChunkPos(anchor), true);
        holdChainAfterEmbody(train);
    }

    // the hold that loaded a body must outlive the embody tick, or sable takes the body away two ticks later and the cycle repeats
    private static boolean holdChainAfterEmbody(SableTrain train) {
        if (!TrainResidency.embodiedWithin(train, TrainResidency.EMBODY_GRACE_TICKS) || controller == null) {
            return false;
        }
        Set<ChunkPos> need = TrainResidency.chainChunks(train);
        if (need.isEmpty()) {
            return false;
        }
        ServerLevel level = train.level();
        Held held = HELD.get(train.id());
        Set<ChunkPos> chunks = new HashSet<>(need);
        ChunkPos center = held == null ? need.iterator().next() : held.center();
        long since = level.getGameTime();
        if (held != null && held.level() == level) {
            chunks.addAll(held.chunks());
            since = held.sinceTick();
        } else if (held != null) {
            release(train.id());
        }
        Set<ChunkPos> already = held != null && held.level() == level ? held.chunks() : Set.of();
        for (ChunkPos pos : chunks) {
            if (!already.contains(pos)) {
                controller.forceChunk(level, train.id(), pos.x, pos.z, true, true);
            }
        }
        HELD.put(train.id(), new Held(level, center, since, chunks));
        return true;
    }

    public static void clearCap(UUID trainId) {
        CAPPED.remove(trainId);
    }

    public static void release(UUID trainId) {
        Held held = HELD.remove(trainId);
        if (held == null) {
            return;
        }
        dropTickets(trainId, held);
    }

    public static void releaseAll() {
        for (Map.Entry<UUID, Held> entry : new HashMap<>(HELD).entrySet()) {
            dropTickets(entry.getKey(), entry.getValue());
        }
        HELD.clear();
        CAPPED.clear();
    }

    public static void retain(Set<UUID> live) {
        CAPPED.retainAll(live);
        for (UUID id : new HashMap<>(HELD).keySet()) {
            if (live.contains(id)) {
                continue;
            }
            release(id);
        }
    }

    public static String describe(SableTrain train) {
        Held held = HELD.get(train.id());
        if (held == null) {
            if (!LoconauticsConfig.STATION_LOAD_CHUNKS.get()) {
                return "station chunk loading is off";
            }
            if (waitsForStationUnloaded(train)) {
                return "no station ticket, the schedule waits for the station to unload";
            }
            return CAPPED.contains(train.id()) ? "station ticket capped out" : "no station ticket";
        }
        return "holding " + held.chunks().size() + " chunks at " + held.center();
    }

    public static String summary() {
        Set<ChunkKey> chunks = new HashSet<>();
        for (Held held : HELD.values()) {
            for (ChunkPos pos : held.chunks()) {
                chunks.add(new ChunkKey(held.level(), pos.toLong()));
            }
        }
        return HELD.size() + " train(s) holding station chunks, " + chunks.size() + " distinct chunk(s) pinned, "
                + CAPPED.size() + " capped out";
    }

    private static void dropTickets(UUID trainId, Held held) {
        if (controller == null) {
            return;
        }
        for (ChunkPos pos : held.chunks()) {
            controller.forceChunk(held.level(), trainId, pos.x, pos.z, false, true);
        }
    }

    private static void acquire(SableTrain train, ChunkPos center, boolean capped) {
        ServerLevel level = train.level();
        Held held = HELD.get(train.id());
        long now = level.getGameTime();
        if (held != null && held.center().equals(center) && held.level() == level) {
            if (capped && now - held.sinceTick() > MAX_DWELL_TICKS) {
                CAPPED.add(train.id());
                release(train.id());
            }
            return;
        }
        if (held != null) {
            release(train.id());
        }
        if (controller == null) {
            return;
        }
        Set<ChunkPos> chunks = new HashSet<>();
        forEachChunk(center, (cx, cz) -> {
            controller.forceChunk(level, train.id(), cx, cz, true, true);
            chunks.add(new ChunkPos(cx, cz));
        });
        HELD.put(train.id(), new Held(level, center, now, chunks));
    }

    private static BlockPos stationAnchorIfEnabled(SableTrain train) {
        return LoconauticsConfig.STATION_LOAD_CHUNKS.get() ? stationAnchor(train) : null;
    }

    // no station means no cargo to move, so a train stopped anywhere else is not worth pinning the world for
    private static BlockPos stationAnchor(SableTrain train) {
        if (train.stationState() != StationState.STOPPED && !train.scheduleHold()) {
            return null;
        }
        // pinning the station would hold the very chunks the wait condition needs to see unloaded
        if (waitsForStationUnloaded(train)) {
            return null;
        }
        GlobalStation station = train.stoppedAt();
        return station == null ? null : station.getBlockEntityPos();
    }

    private static boolean waitsForStationUnloaded(SableTrain train) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
        return runtime != null && runtime.waitsForStationUnloaded();
    }

    private static void forEachChunk(ChunkPos center, ChunkAction action) {
        for (int cx = center.x - RADIUS_CHUNKS; cx <= center.x + RADIUS_CHUNKS; cx++) {
            for (int cz = center.z - RADIUS_CHUNKS; cz <= center.z + RADIUS_CHUNKS; cz++) {
                action.run(cx, cz);
            }
        }
    }

    @FunctionalInterface
    private interface ChunkAction {
        void run(int chunkX, int chunkZ);
    }
}
