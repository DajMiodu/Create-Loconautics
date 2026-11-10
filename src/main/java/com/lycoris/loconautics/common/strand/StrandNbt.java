package com.lycoris.loconautics.common.strand;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StrandNbt {

    private StrandNbt() {
    }

    public static CompoundTag write(UUID id, @Nullable UUID subA, BlockPos localA, @Nullable UUID subB,
                                    BlockPos localB, String typeId, double restLength,
                                    @Nullable List<Vector3d> points) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        if (subA != null) {
            tag.putUUID("SubA", subA);
        }
        tag.putLong("LocalA", localA.asLong());
        if (subB != null) {
            tag.putUUID("SubB", subB);
        }
        tag.putLong("LocalB", localB.asLong());
        tag.putString("Type", typeId);
        tag.putDouble("RestLength", restLength);
        if (points != null && points.size() >= 2) {
            ListTag list = new ListTag();
            for (Vector3d p : points) {
                list.add(DoubleTag.valueOf(p.x));
                list.add(DoubleTag.valueOf(p.y));
                list.add(DoubleTag.valueOf(p.z));
            }
            tag.put("Points", list);
        }
        return tag;
    }

    @Nullable
    public static List<Vector3d> readPoints(CompoundTag tag) {
        if (!tag.contains("Points")) {
            return null;
        }
        ListTag list = tag.getList("Points", Tag.TAG_DOUBLE);
        List<Vector3d> points = new ArrayList<>(list.size() / 3);
        for (int i = 0; i + 2 < list.size(); i += 3) {
            points.add(new Vector3d(list.getDouble(i), list.getDouble(i + 1), list.getDouble(i + 2)));
        }
        return points;
    }
}
