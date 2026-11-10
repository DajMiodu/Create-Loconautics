package com.lycoris.loconautics.common.sable;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SubLevelBlocks {

    @FunctionalInterface
    public interface BlockVisitor {
        boolean visit(BlockPos pos, BlockState state);
    }

    private SubLevelBlocks() {
    }

    public static void forEachBlock(SubLevel sub, BlockVisitor visitor) {
        if (sub == null || sub.isRemoved()) {
            return;
        }
        var bounds = sub.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (PlotChunkHolder holder : sub.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            ChunkPos cp = chunk.getPos();
            int minX = Math.max(bounds.minX(), cp.getMinBlockX());
            int maxX = Math.min(bounds.maxX(), cp.getMaxBlockX());
            int minZ = Math.max(bounds.minZ(), cp.getMinBlockZ());
            int maxZ = Math.min(bounds.maxZ(), cp.getMaxBlockZ());
            int firstSection = Math.max(0, chunk.getSectionIndex(bounds.minY()));
            int lastSection = Math.min(chunk.getSectionsCount() - 1, chunk.getSectionIndex(bounds.maxY()));
            for (int si = firstSection; si <= lastSection; si++) {
                LevelChunkSection section = chunk.getSection(si);
                if (section.hasOnlyAir()) {
                    continue;
                }
                int sectionMinY = chunk.getMinSection() * 16 + si * 16;
                int minY = Math.max(bounds.minY(), sectionMinY);
                int maxY = Math.min(bounds.maxY(), sectionMinY + 15);
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            cursor.set(x, y, z);
                            if (!visitor.visit(cursor, section.getBlockState(x & 15, y & 15, z & 15))) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean hasBlock(SubLevel sub, Class<? extends Block> type) {
        boolean[] found = {false};
        forEachBlock(sub, (pos, state) -> {
            if (type.isInstance(state.getBlock())) {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }

    public static int countBlocks(SubLevel sub) {
        int[] count = {0};
        forEachBlock(sub, (pos, state) -> {
            if (!state.isAir()) {
                count[0]++;
            }
            return true;
        });
        return count[0];
    }

    private record Cached(int count, List<BlockEntity> entities) {
    }

    private static final Map<SubLevel, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    public static List<BlockEntity> blockEntities(SubLevel sub) {
        if (sub == null || sub.isRemoved()) {
            return List.of();
        }
        int count = 0;
        for (PlotChunkHolder holder : sub.getPlot().getLoadedChunks()) {
            count += holder.getChunk().getBlockEntities().size();
        }
        Cached cached = CACHE.get(sub);
        if (cached != null && cached.count() == count && cached.entities().stream().noneMatch(BlockEntity::isRemoved)) {
            return cached.entities();
        }
        List<BlockEntity> out = new ArrayList<>(count);
        for (PlotChunkHolder holder : sub.getPlot().getLoadedChunks()) {
            out.addAll(holder.getChunk().getBlockEntities().values());
        }
        List<BlockEntity> fresh = Collections.unmodifiableList(out);
        CACHE.put(sub, new Cached(count, fresh));
        return fresh;
    }

    @Nullable
    public static <T extends BlockEntity> T find(SubLevel sub, Class<T> type) {
        for (BlockEntity be : blockEntities(sub)) {
            if (type.isInstance(be)) {
                return type.cast(be);
            }
        }
        return null;
    }

    public static <T extends BlockEntity> List<T> findAll(SubLevel sub, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (BlockEntity be : blockEntities(sub)) {
            if (type.isInstance(be)) {
                out.add(type.cast(be));
            }
        }
        return out;
    }
}
