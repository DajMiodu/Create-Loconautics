package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.StrandNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public final class ScrewLinkStrand {

    public static final double LENGTH = 2.0;

    private static final String TYPE_ID = "screw_link";

    public final UUID id;

    @Nullable public UUID subA;
    public BlockPos localA;

    @Nullable public UUID subB;
    @Nullable public BlockPos localB;

    @Nullable public List<Vector3d> points;

    public ScrewLinkStrand(UUID id, @Nullable UUID subA, BlockPos localA) {
        this.id = id;
        this.subA = subA;
        this.localA = localA;
    }

    public boolean isCoupled() {
        return localB != null;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = StrandNbt.write(id, subA, localA, subB, localB == null ? localA : localB,
                TYPE_ID, LENGTH, points);
        tag.putBoolean("Coupled", isCoupled());
        return tag;
    }

    public static ScrewLinkStrand fromNbt(CompoundTag tag) {
        ScrewLinkStrand s = new ScrewLinkStrand(
                tag.getUUID("Id"),
                tag.hasUUID("SubA") ? tag.getUUID("SubA") : null,
                BlockPos.of(tag.getLong("LocalA")));
        if (tag.getBoolean("Coupled")) {
            s.subB = tag.hasUUID("SubB") ? tag.getUUID("SubB") : null;
            s.localB = BlockPos.of(tag.getLong("LocalB"));
        }
        s.points = StrandNbt.readPoints(tag);
        return s;
    }
}
