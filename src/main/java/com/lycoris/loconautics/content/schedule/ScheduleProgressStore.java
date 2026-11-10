package com.lycoris.loconautics.content.schedule;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ScheduleProgressStore extends SavedData {

    private static final String DATA_NAME = "loconautics_schedules";

    private final Map<UUID, CompoundTag> progress = new HashMap<>();

    private final Map<UUID, CompoundTag> schedules = new HashMap<>();

    public static ScheduleProgressStore get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(ScheduleProgressStore::new, ScheduleProgressStore::load, null), DATA_NAME);
    }

    public void putProgress(UUID trainId, CompoundTag state) {
        progress.put(trainId, state.copy());
        setDirty();
    }

    public CompoundTag getProgress(UUID trainId) {
        CompoundTag record = progress.get(trainId);
        return record == null ? null : record.copy();
    }

    public void putSchedule(UUID trainId, CompoundTag schedule) {
        schedules.put(trainId, schedule.copy());
        setDirty();
    }

    public CompoundTag getSchedule(UUID trainId) {
        CompoundTag record = schedules.get(trainId);
        return record == null ? null : record.copy();
    }

    public boolean hasSchedule(UUID trainId) {
        return schedules.containsKey(trainId);
    }

    public void move(UUID from, UUID to) {
        CompoundTag schedule = schedules.remove(from);
        CompoundTag state = progress.remove(from);
        if (schedule != null) {
            schedules.put(to, schedule);
        }
        if (state != null) {
            progress.put(to, state);
        }
        setDirty();
    }

    public void remove(UUID trainId) {
        boolean removed = progress.remove(trainId) != null;
        removed |= schedules.remove(trainId) != null;
        if (removed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        for (Map.Entry<UUID, CompoundTag> e : progress.entrySet()) {
            entries.put(e.getKey().toString(), e.getValue().copy());
        }
        tag.put("Progress", entries);
        CompoundTag held = new CompoundTag();
        for (Map.Entry<UUID, CompoundTag> e : schedules.entrySet()) {
            held.put(e.getKey().toString(), e.getValue().copy());
        }
        tag.put("Schedules", held);
        return tag;
    }

    private static ScheduleProgressStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ScheduleProgressStore store = new ScheduleProgressStore();
        CompoundTag entries = tag.getCompound("Progress");
        for (String key : entries.getAllKeys()) {
            store.progress.put(UUID.fromString(key), entries.getCompound(key));
        }
        CompoundTag held = tag.getCompound("Schedules");
        for (String key : held.getAllKeys()) {
            store.schedules.put(UUID.fromString(key), held.getCompound(key));
        }
        return store;
    }
}
