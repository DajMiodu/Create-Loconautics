package com.lycoris.loconautics.content.trainlookup.history;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrainHistory extends SavedData {

    private static final String DATA_NAME = "loconautics_train_history";

    private final List<HistoryEntry> entries = new ArrayList<>();
    private final Map<UUID, List<HistoryEntry>> byTrain = new HashMap<>();
    private final Map<UUID, List<HistoryEntry>> byActor = new HashMap<>();

    public TrainHistory() {
    }

    public static TrainHistory get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(TrainHistory::new, TrainHistory::load, null), DATA_NAME);
    }

    public void record(HistoryEntry entry) {
        if (entry == null) {
            return;
        }
        index(entry);
        setDirty();
    }

    private void index(HistoryEntry entry) {
        entries.add(entry);
        byTrain.computeIfAbsent(entry.trainUUID(), k -> new ArrayList<>()).add(entry);
        byActor.computeIfAbsent(entry.actorUUID(), k -> new ArrayList<>()).add(entry);
    }

    public List<HistoryEntry> forTrain(UUID trainId) {
        return Collections.unmodifiableList(byTrain.getOrDefault(trainId, List.of()));
    }

    public List<HistoryEntry> forPlayer(UUID actorId) {
        return Collections.unmodifiableList(byActor.getOrDefault(actorId, List.of()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (HistoryEntry entry : entries) {
            list.add(entry.save());
        }
        tag.put("Entries", list);
        return tag;
    }

    private static TrainHistory load(CompoundTag tag, HolderLookup.Provider registries) {
        TrainHistory history = new TrainHistory();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            HistoryEntry entry = HistoryEntry.load(list.getCompound(i));
            if (entry != null) {
                history.index(entry);
            }
        }
        return history;
    }
}
