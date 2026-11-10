package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.common.strand.StrandNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public final class HoseStrand {

    public final UUID id;
    public BlockPos winchPos;

    public BlockPos nozzlePos;

    @Nullable public UUID nozzleSubLevelId;
    @Nullable public BlockPos nozzleLocalPos;

    public HoseType type;
    public float currentLength;
    public boolean locked;
    public int fluidBuffer;

    @Nullable public List<Vector3d> points;
    public double extensionGoal = 1.0;

    public HoseStrand(UUID id, BlockPos winchPos, HoseType type) {
        this.id = id;
        this.winchPos = winchPos;
        this.type = type;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putLong("WinchPos", winchPos.asLong());
        if (nozzlePos != null) {
            tag.putLong("NozzlePos", nozzlePos.asLong());
        }
        if (nozzleSubLevelId != null) {
            tag.putUUID("NozzleSub", nozzleSubLevelId);
        }
        if (nozzleLocalPos != null) {
            tag.putLong("NozzleLocal", nozzleLocalPos.asLong());
        }
        tag.putString("Type", type.id().toString());
        tag.putFloat("Length", currentLength);
        tag.putBoolean("Locked", locked);
        tag.putInt("Fluid", fluidBuffer);
        if (points != null && points.size() >= 2) {
            ListTag list = new ListTag();
            for (Vector3d p : points) {
                list.add(DoubleTag.valueOf(p.x));
                list.add(DoubleTag.valueOf(p.y));
                list.add(DoubleTag.valueOf(p.z));
            }
            tag.put("Points", list);
            tag.putDouble("ExtGoal", extensionGoal);
        }
        return tag;
    }

    public static HoseStrand fromNbt(CompoundTag tag) {
        HoseType type = HoseType.get(ResourceLocation.parse(tag.getString("Type")));
        HoseStrand s = new HoseStrand(tag.getUUID("Id"), BlockPos.of(tag.getLong("WinchPos")), type);
        if (tag.contains("NozzlePos")) {
            s.nozzlePos = BlockPos.of(tag.getLong("NozzlePos"));
        }
        if (tag.contains("NozzleSub")) {
            s.nozzleSubLevelId = tag.getUUID("NozzleSub");
        }
        if (tag.contains("NozzleLocal")) {
            s.nozzleLocalPos = BlockPos.of(tag.getLong("NozzleLocal"));
        }
        s.currentLength = tag.getFloat("Length");
        s.locked = tag.getBoolean("Locked");
        s.fluidBuffer = tag.getInt("Fluid");
        s.points = StrandNbt.readPoints(tag);
        if (s.points != null) {
            s.extensionGoal = tag.getDouble("ExtGoal");
        }
        return s;
    }
}
