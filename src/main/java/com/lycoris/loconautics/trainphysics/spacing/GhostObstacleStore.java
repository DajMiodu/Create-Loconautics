package com.lycoris.loconautics.trainphysics.spacing;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

final class GhostObstacleStore extends SavedData {

    private static final int VERSION = 2;
    private static final String DATA_NAME = "loconautics_ghost_obstacles";
    private static final SavedData.Factory<GhostObstacleStore> FACTORY =
            new SavedData.Factory<>(GhostObstacleStore::new, GhostObstacleStore::load, null);

    private final Long2ObjectMap<LongSet> cells = new Long2ObjectOpenHashMap<>();

    static GhostObstacleStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    boolean isEmpty() {
        return cells.isEmpty();
    }

    boolean has(long chunk, long cell) {
        LongSet set = cells.get(chunk);
        return set != null && set.contains(cell);
    }

    void replace(long chunk, LongSet found) {
        LongSet old = found == null || found.isEmpty() ? cells.remove(chunk) : cells.put(chunk, found);
        if (old != null || (found != null && !found.isEmpty())) {
            setDirty();
        }
    }

    void add(long chunk, LongSet found) {
        if (cells.computeIfAbsent(chunk, key -> new LongOpenHashSet()).addAll(found)) {
            setDirty();
        }
    }

    int size(long chunk) {
        LongSet set = cells.get(chunk);
        return set == null ? 0 : set.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Long2ObjectMap.Entry<LongSet> entry : cells.long2ObjectEntrySet()) {
            CompoundTag record = new CompoundTag();
            record.putLong("Chunk", entry.getLongKey());
            record.putLongArray("Cells", entry.getValue().toLongArray());
            list.add(record);
        }
        tag.putInt("Version", VERSION);
        tag.put("Chunks", list);
        return tag;
    }

    private static GhostObstacleStore load(CompoundTag tag, HolderLookup.Provider registries) {
        GhostObstacleStore store = new GhostObstacleStore();
        if (tag.getInt("Version") != VERSION) {
            return store;
        }
        for (Tag raw : tag.getList("Chunks", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            store.cells.put(record.getLong("Chunk"), new LongOpenHashSet(record.getLongArray("Cells")));
        }
        return store;
    }
}
