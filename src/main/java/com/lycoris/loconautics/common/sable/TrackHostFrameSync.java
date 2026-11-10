package com.lycoris.loconautics.common.sable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.network.packets.train.SubLevelFrameSyncPacket;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class TrackHostFrameSync {

    private static final int SYNC_INTERVAL_TICKS = 3;
    private static final int SWEEP_INTERVAL_TICKS = 200;
    private static final int SWEEP_DELAY_TICKS = 600;

    @Nullable
    private static TrackHostFrameStore store;
    private static int tick;
    private static int lastSyncTick;
    private static int lastSignature;
    private static boolean syncNeeded;

    private TrackHostFrameSync() {
    }

    public static void markDirty() {
        TrackHostFrameStore current = store;
        if (current != null) {
            current.setDirty();
        }
    }

    public static void requestSync() {
        syncNeeded = true;
    }

    public static void onUnloaded(ServerSubLevel sub, ResourceKey<Level> dimension) {
        TrackHostFrames.onUnloaded(sub, dimension);
        syncNeeded = true;
        markDirty();
    }

    public static void onLoaded(UUID host) {
        TrackHostFrames.onLoaded(host);
        syncNeeded = true;
        markDirty();
    }

    public static void onRemoved(UUID host) {
        if (TrackHostFrames.frameOf(host) == null) {
            return;
        }
        TrackHostFrames.forget(host);
        syncNeeded = true;
        markDirty();
    }

    public static void serverTick(MinecraftServer server) {
        tick++;
        if (tick > SWEEP_DELAY_TICKS && tick % SWEEP_INTERVAL_TICKS == 0) {
            for (UUID orphan : TrackHostFrames.orphans()) {
                onRemoved(orphan);
            }
            markDirty();
        }
        int signature = signature();
        if ((syncNeeded || signature != lastSignature) && tick - lastSyncTick >= SYNC_INTERVAL_TICKS) {
            lastSyncTick = tick;
            lastSignature = signature;
            syncNeeded = false;
            PacketDistributor.sendToAllPlayers(packet());
        }
    }

    private static int signature() {
        int hash = 0;
        for (TrackHostFrames.Frame frame : TrackHostFrames.unloaded()) {
            hash += 31 * frame.host().hashCode() + Double.hashCode(frame.angle());
        }
        return hash;
    }

    private static SubLevelFrameSyncPacket packet() {
        List<FramePose> frames = new ArrayList<>();
        for (TrackHostFrames.Frame frame : TrackHostFrames.unloaded()) {
            frames.add(FramePose.of(frame));
        }
        return new SubLevelFrameSyncPacket(frames);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        store = TrackHostFrameStore.get(event.getServer());
        for (TrackHostFrames.Frame frame : store.loaded()) {
            TrackHostFrames.restore(frame);
        }
        store.loaded().clear();
        syncNeeded = true;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            for (SubLevel sub : container.getAllSubLevels()) {
                if (sub instanceof ServerSubLevel server && !server.isRemoved()) {
                    TrackHostFrames.onUnloaded(server, level.dimension());
                }
            }
        }
        markDirty();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TrackHostFrames.clear();
        store = null;
        tick = 0;
        lastSyncTick = 0;
        lastSignature = 0;
        syncNeeded = false;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, packet());
        }
    }
}
