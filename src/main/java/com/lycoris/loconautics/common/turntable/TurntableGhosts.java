package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.TrackHostFrameSync;
import com.lycoris.loconautics.common.sable.TrackHostFrames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class TurntableGhosts {

    private static final int SAVE_INTERVAL_TICKS = 200;

    private static final Map<UUID, GhostTurntable> GHOSTS = new ConcurrentHashMap<>();
    private static final Set<Source> LIVE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FAILED = ConcurrentHashMap.newKeySet();

    @Nullable
    private static TurntableGhostStore store;
    private static int tick;

    static {
        TrackHostFrames.onForget(TurntableGhosts::drop);
    }

    public interface Source {

        @Nullable
        GhostTurntable toGhost();
    }

    private TurntableGhosts() {
    }

    public static Collection<GhostTurntable> all() {
        return new ArrayList<>(GHOSTS.values());
    }

    public static void trackLive(Source source) {
        LIVE.add(source);
    }

    public static void untrackLive(Source source) {
        LIVE.remove(source);
    }

    public static void markDirty() {
        TurntableGhostStore current = store;
        if (current != null) {
            current.setDirty();
        }
    }

    public static void ghostify(Source source) {
        GhostTurntable ghost = source.toGhost();
        if (ghost == null || ghost.frame() == null) {
            return;
        }
        GHOSTS.put(ghost.plateSubLevelId(), ghost);
        if (ghost.turntableScheduleDriven()) {
            TurntableRegistry.register(ghost);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.overworld() != null && store == null) {
            store = TurntableGhostStore.get(server);
        }
        TrackHostFrameSync.requestSync();
        markDirty();
    }

    @Nullable
    public static GhostTurntable take(@Nullable UUID plate) {
        GhostTurntable ghost = plate == null ? null : GHOSTS.remove(plate);
        if (ghost == null) {
            return null;
        }
        TurntableRegistry.dropGhost(ghost);
        TrackHostFrameSync.requestSync();
        markDirty();
        return ghost;
    }

    public static void drop(UUID plate) {
        GhostTurntable ghost = GHOSTS.remove(plate);
        if (ghost == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(ghost.dimension());
        ghost.signals().release(level);
        TurntableRegistry.unregister(plate);
        TurntableRegistry.forgetPlate(plate);
        FAILED.remove(plate);
        markDirty();
    }

    public static void serverTick(MinecraftServer server) {
        tick++;
        if (GHOSTS.isEmpty()) {
            return;
        }
        for (GhostTurntable ghost : GHOSTS.values()) {
            tickOne(ghost, server);
        }
        if (tick % SAVE_INTERVAL_TICKS == 0) {
            markDirty();
        }
    }

    private static void tickOne(GhostTurntable ghost, MinecraftServer server) {
        try {
            ghost.tick(server);
            if (ghost.orphaned(server)) {
                TrackHostFrames.forget(ghost.plateSubLevelId());
                drop(ghost.plateSubLevelId());
            }
        } catch (RuntimeException e) {
            if (FAILED.add(ghost.plateSubLevelId())) {
                LoconauticsConstants.LOGGER.error("[turntableghost] plate {} failed to tick, it will keep failing quietly",
                        shortId(ghost.plateSubLevelId()), e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        store = TurntableGhostStore.get(server);
        for (CompoundTag tag : store.loaded()) {
            GhostTurntable ghost = GhostTurntable.read(tag);
            if (ghost == null || TurntableRegistry.byPlate(ghost.plateSubLevelId()) != null) {
                continue;
            }
            GHOSTS.put(ghost.plateSubLevelId(), ghost);
            if (ghost.turntableScheduleDriven()) {
                TurntableRegistry.register(ghost);
            }
        }
        store.loaded().clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Source source : new ArrayList<>(LIVE)) {
            ghostify(source);
        }
        markDirty();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GHOSTS.clear();
        LIVE.clear();
        FAILED.clear();
        store = null;
        tick = 0;
    }
}
