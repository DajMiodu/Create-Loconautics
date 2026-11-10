package com.lycoris.loconautics.common.sable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.List;

public final class TrackHostFrameStore extends SavedData {

    private static final String DATA_NAME = "loconautics_track_host_frames";

    private final List<TrackHostFrames.Frame> loaded = new ArrayList<>();

    public static TrackHostFrameStore get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(TrackHostFrameStore::new, TrackHostFrameStore::load, null), DATA_NAME);
    }

    List<TrackHostFrames.Frame> loaded() {
        return loaded;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (TrackHostFrames.Frame frame : TrackHostFrames.unloaded()) {
            list.add(frame.write());
        }
        tag.put("Frames", list);
        return tag;
    }

    private static TrackHostFrameStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TrackHostFrameStore store = new TrackHostFrameStore();
        ListTag list = tag.getList("Frames", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            TrackHostFrames.Frame frame = TrackHostFrames.read(list.getCompound(i));
            if (frame != null) {
                store.loaded.add(frame);
            }
        }
        return store;
    }
}
