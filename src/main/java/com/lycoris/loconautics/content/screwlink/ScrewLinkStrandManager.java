package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.StrandManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ScrewLinkStrandManager extends StrandManager<ScrewLinkStrand> {

    private static final String DATA_NAME = "loconautics_screw_link_strands";

    public static ScrewLinkStrandManager get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(ScrewLinkStrandManager::new, ScrewLinkStrandManager::load, null), DATA_NAME);
    }

    @Nullable
    public static ScrewLinkStrandManager get(Level level) {
        if (level.isClientSide || level.getServer() == null) {
            return null;
        }
        return get(level.getServer());
    }

    @Override
    protected UUID idOf(ScrewLinkStrand strand) {
        return strand.id;
    }

    @Override
    protected CompoundTag toNbt(ScrewLinkStrand strand) {
        return strand.toNbt();
    }

    private static ScrewLinkStrandManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ScrewLinkStrandManager m = new ScrewLinkStrandManager();
        loadInto(m, tag, ScrewLinkStrand::fromNbt);
        return m;
    }
}
