package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.common.SavedDataStores;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public abstract class StrandManager<T> extends SavedData {

    protected final Map<UUID, T> strands = new HashMap<>();

    protected abstract UUID idOf(T strand);

    protected abstract CompoundTag toNbt(T strand);

    public T create(T strand) {
        strands.put(idOf(strand), strand);
        setDirty();
        return strand;
    }

    @Nullable
    public T get(UUID id) {
        return id == null ? null : strands.get(id);
    }

    public Collection<T> all() {
        return strands.values();
    }

    public void remove(UUID id) {
        SavedDataStores.remove(this, strands, id);
    }

    public void touch() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (T s : strands.values()) {
            list.add(toNbt(s));
        }
        tag.put("Strands", list);
        return tag;
    }

    protected static <M extends StrandManager<T>, T> void loadInto(M manager, CompoundTag tag, Function<CompoundTag, T> fromNbt) {
        ListTag list = tag.getList("Strands", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            T s = fromNbt.apply(list.getCompound(i));
            manager.strands.put(manager.idOf(s), s);
        }
    }
}
