package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.StrandManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class SteelCableStrandManager extends StrandManager<SteelCableStrand> {

    private static final String DATA_NAME = "loconautics_steel_cable_strands";

    public static SteelCableStrandManager get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(SteelCableStrandManager::new, SteelCableStrandManager::load, null), DATA_NAME);
    }

    @Nullable
    public static SteelCableStrandManager get(Level level) {
        if (level.isClientSide || level.getServer() == null) {
            return null;
        }
        return get(level.getServer());
    }

    @Override
    protected UUID idOf(SteelCableStrand strand) {
        return strand.id;
    }

    @Override
    protected CompoundTag toNbt(SteelCableStrand strand) {
        return strand.toNbt();
    }

    private static SteelCableStrandManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SteelCableStrandManager m = new SteelCableStrandManager();
        loadInto(m, tag, SteelCableStrand::fromNbt);
        return m;
    }
}
