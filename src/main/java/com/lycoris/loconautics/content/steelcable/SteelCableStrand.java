package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.StrandNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SteelCableStrand {

    public final UUID id;

    @Nullable public UUID subA;
    public BlockPos localA;
    @Nullable public UUID subB;
    public BlockPos localB;

    public SteelCableType type;

    public double restLength;

    @Nullable public List<Vector3d> points;

    public double extensionGoal = 1.0;

    public SteelCableStrand(UUID id, @Nullable UUID subA, BlockPos localA,
                            @Nullable UUID subB, BlockPos localB, SteelCableType type) {
        this.id = id;
        this.subA = subA;
        this.localA = localA;
        this.subB = subB;
        this.localB = localB;
        this.type = type;
    }

    public boolean isOwner(@Nullable UUID sub, BlockPos local) {
        return Objects.equals(subA, sub) && localA.equals(local);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = StrandNbt.write(
                id, subA, localA, subB, localB, type.id().toString(), restLength, points);
        tag.putDouble("ExtensionGoal", extensionGoal);
        return tag;
    }

    public static SteelCableStrand fromNbt(CompoundTag tag) {
        SteelCableStrand s = new SteelCableStrand(
                tag.getUUID("Id"),
                tag.hasUUID("SubA") ? tag.getUUID("SubA") : null,
                BlockPos.of(tag.getLong("LocalA")),
                tag.hasUUID("SubB") ? tag.getUUID("SubB") : null,
                BlockPos.of(tag.getLong("LocalB")),
                SteelCableType.get(ResourceLocation.parse(tag.getString("Type"))));
        s.restLength = tag.getDouble("RestLength");
        s.points = StrandNbt.readPoints(tag);
        if (tag.contains("ExtensionGoal")) {
            s.extensionGoal = tag.getDouble("ExtensionGoal");
        }
        return s;
    }
}
