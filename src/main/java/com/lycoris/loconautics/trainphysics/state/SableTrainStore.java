package com.lycoris.loconautics.trainphysics.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SableTrainStore extends SavedData {

    private static final String DATA_NAME = "loconautics_sable_trains";

    private final Map<UUID, CompoundTag> records = new HashMap<>();

    public SableTrainStore() {
    }

    public static SableTrainStore get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(SableTrainStore::new, SableTrainStore::load, null), DATA_NAME);
    }

    public void put(UUID id, CompoundTag record) {
        records.put(id, record);
        setDirty();
    }

    public void remove(UUID id) {
        com.lycoris.loconautics.common.SavedDataStores.remove(this, records, id);
    }

    public Map<UUID, CompoundTag> records() {
        return records;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CompoundTag record : records.values()) {
            list.add(record.copy());
        }
        tag.put("Trains", list);
        return tag;
    }

    private static SableTrainStore load(CompoundTag tag, HolderLookup.Provider registries) {
        SableTrainStore store = new SableTrainStore();
        ListTag list = tag.getList("Trains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            if (record.hasUUID("Id")) {
                store.records.put(record.getUUID("Id"), record);
            }
        }
        return store;
    }
}
