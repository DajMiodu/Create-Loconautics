package com.lycoris.loconautics.common;

import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;

public final class SavedDataStores {

    private SavedDataStores() {
    }

    public static <K, V> void remove(SavedData store, Map<K, V> entries, K key) {
        if (entries.remove(key) != null) {
            store.setDirty();
        }
    }
}
