package com.lycoris.loconautics.content.schedule.map;

import com.simibubi.create.content.trains.track.ITrackBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChunkColorSamplerHandler {

    private static final TagKey<Block> CREATE_TRACKS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", "tracks"));

    private static ChunkColorSamplerHandler instance;

    public static ChunkColorSamplerHandler get() {
        return instance;
    }

    public ChunkColorSamplerHandler() {
        instance = this;
    }

    private record PendingSample(ServerLevel level, ChunkPos pos, boolean force) {}

    private final Queue<PendingSample> pending = new ConcurrentLinkedQueue<>();

    public void forceResample(ServerLevel level, ChunkPos pos) {
        pending.add(new PendingSample(level, pos, true));
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ChunkPos pos = event.getChunk().getPos();
        if (!hasNearbyPlayer(level, pos)) return;

        pending.add(new PendingSample(level, pos, false));
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof ITrackBlock) && !state.is(CREATE_TRACKS)) return;
        forceResample(level, new ChunkPos(event.getPos()));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        int processed = 0;
        PendingSample sample;
        while (processed < 16 && (sample = pending.poll()) != null) {
            processChunk(sample.level, sample.pos, sample.force);
            processed++;
        }
    }

    private void processChunk(ServerLevel level, ChunkPos pos, boolean force) {
        if (!force && MapColorStorage.get(level).read(pos.x, pos.z) != null) return;

        if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) return;

        byte[] colors = ChunkMapColorSampler.sample(level, pos);
        MapColorStorage.get(level).write(pos.x, pos.z, colors);
    }

    private boolean hasNearbyPlayer(ServerLevel level, ChunkPos pos) {
        int centerX = pos.getMiddleBlockX();
        int centerZ = pos.getMiddleBlockZ();
        return level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.level() == level)
                .anyMatch(p -> {
                    double dx = p.getX() - centerX;
                    double dz = p.getZ() - centerZ;
                    return dx * dx + dz * dz < 128 * 128;
                });
    }
}
