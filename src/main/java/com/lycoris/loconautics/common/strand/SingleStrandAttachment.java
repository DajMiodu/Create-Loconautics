package com.lycoris.loconautics.common.strand;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface SingleStrandAttachment {

    @Nullable
    UUID rawAttachedStrandId();

    void rawAttachedStrandId(@Nullable UUID id);

    void markChanged();

    default void setAttachedStrandId(@Nullable UUID id) {
        rawAttachedStrandId(id);
        markChanged();
    }

    default void writeAttachedStrand(CompoundTag nbt) {
        UUID id = rawAttachedStrandId();
        if (id != null) {
            nbt.putUUID("AttachedStrand", id);
        }
    }

    default void readAttachedStrand(CompoundTag nbt) {
        rawAttachedStrandId(nbt.hasUUID("AttachedStrand") ? nbt.getUUID("AttachedStrand") : null);
    }
}
