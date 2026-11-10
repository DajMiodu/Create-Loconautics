package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.common.strand.StrandManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class HoseStrandManager extends StrandManager<HoseStrand> {

    private static final String DATA_NAME = "loconautics_hose_strands";

    public static HoseStrandManager get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(HoseStrandManager::new, HoseStrandManager::load, null), DATA_NAME);
    }

    @Nullable
    public static HoseStrandManager get(Level level) {
        if (level.isClientSide || level.getServer() == null) {
            return null;
        }
        return get(level.getServer());
    }

    @Override
    protected UUID idOf(HoseStrand strand) {
        return strand.id;
    }

    @Override
    protected CompoundTag toNbt(HoseStrand strand) {
        return strand.toNbt();
    }

    private static HoseStrandManager load(CompoundTag tag, HolderLookup.Provider registries) {
        HoseStrandManager m = new HoseStrandManager();
        loadInto(m, tag, HoseStrand::fromNbt);
        return m;
    }
}
