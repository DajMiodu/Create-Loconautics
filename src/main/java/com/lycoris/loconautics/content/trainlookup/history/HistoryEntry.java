package com.lycoris.loconautics.content.trainlookup.history;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record HistoryEntry(
        long timestamp,
        HistoryEvent event,
        UUID trainUUID,
        @Nullable String trainName,
        UUID actorUUID,
        String actorName,
        ResourceKey<Level> dimension,
        Vec3 position,
        int carriageIndex,
        @Nullable String carriageLabel,
        @Nullable String notes) {

    public static final int NO_CARRIAGE = -1;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Time", timestamp);
        tag.putString("Event", event.name());
        tag.putUUID("Train", trainUUID);
        tag.putUUID("Actor", actorUUID);
        tag.putString("ActorName", actorName);
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putInt("Carriage", carriageIndex);
        if (trainName != null) {
            tag.putString("TrainName", trainName);
        }
        if (carriageLabel != null) {
            tag.putString("CarriageLabel", carriageLabel);
        }
        if (notes != null) {
            tag.putString("Notes", notes);
        }
        return tag;
    }

    @Nullable
    public static HistoryEntry load(CompoundTag tag) {
        HistoryEvent event = HistoryEvent.byName(tag.getString("Event"));
        if (event == null || !tag.hasUUID("Train") || !tag.hasUUID("Actor")) {
            return null;
        }
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dim == null) {
            return null;
        }
        return new HistoryEntry(
                tag.getLong("Time"),
                event,
                tag.getUUID("Train"),
                tag.contains("TrainName") ? tag.getString("TrainName") : null,
                tag.getUUID("Actor"),
                tag.getString("ActorName"),
                ResourceKey.create(Registries.DIMENSION, dim),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                tag.getInt("Carriage"),
                tag.contains("CarriageLabel") ? tag.getString("CarriageLabel") : null,
                tag.contains("Notes") ? tag.getString("Notes") : null);
    }
}
