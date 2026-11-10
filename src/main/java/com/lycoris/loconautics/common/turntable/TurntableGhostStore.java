package com.lycoris.loconautics.common.turntable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.List;

public final class TurntableGhostStore extends SavedData {

    private static final String DATA_NAME = "loconautics_turntable_ghosts";

    private final List<CompoundTag> loaded = new ArrayList<>();

    public static TurntableGhostStore get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(TurntableGhostStore::new, TurntableGhostStore::load, null), DATA_NAME);
    }

    List<CompoundTag> loaded() {
        return loaded;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (GhostTurntable ghost : TurntableGhosts.all()) {
            list.add(ghost.write());
        }
        tag.put("Ghosts", list);
        return tag;
    }

    private static TurntableGhostStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TurntableGhostStore store = new TurntableGhostStore();
        ListTag list = tag.getList("Ghosts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            store.loaded.add(list.getCompound(i));
        }
        return store;
    }
}
