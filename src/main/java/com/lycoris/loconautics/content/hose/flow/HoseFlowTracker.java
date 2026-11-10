package com.lycoris.loconautics.content.hose.flow;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.network.packets.attachment.HoseFlowPacket;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class HoseFlowTracker {

    private static final long LINGER_TICKS = 4;
    private static final long RESEND_TICKS = 40;

    private record Mark(long tick, boolean forward) {
    }

    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
    private static HoseFlowPacket lastSent = new HoseFlowPacket(List.of(), List.of());
    private static long lastSentTick;

    private HoseFlowTracker() {
    }

    public static void mark(UUID strandId, boolean forward, long gameTime) {
        MARKS.put(strandId, new Mark(gameTime, forward));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        long now = level.getGameTime();
        MARKS.entrySet().removeIf(e -> now - e.getValue().tick() > LINGER_TICKS);
        List<UUID> forward = new ArrayList<>();
        List<UUID> backward = new ArrayList<>();
        for (Map.Entry<UUID, Mark> e : MARKS.entrySet()) {
            (e.getValue().forward() ? forward : backward).add(e.getKey());
        }
        boolean changed = !sameIds(forward, lastSent.forward()) || !sameIds(backward, lastSent.backward());
        if (changed || now - lastSentTick >= RESEND_TICKS) {
            lastSent = new HoseFlowPacket(forward, backward);
            lastSentTick = now;
            PacketDistributor.sendToPlayersInDimension(level, lastSent);
        }
    }

    private static boolean sameIds(List<UUID> a, List<UUID> b) {
        return a.size() == b.size() && new HashSet<>(a).equals(new HashSet<>(b));
    }
}
