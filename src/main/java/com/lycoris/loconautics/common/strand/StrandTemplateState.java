package com.lycoris.loconautics.common.strand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class StrandTemplateState {

    private static final String RECORDS = "StrandRecords";
    private static final String PENDING = "StrandPending";
    private static final int GIVE_UP_TICKS = 200;

    private final StrandTemplateKind kind;
    private final List<CompoundTag> records = new ArrayList<>();
    private final List<CompoundTag> pending = new ArrayList<>();
    private int waited;

    public StrandTemplateState(StrandTemplateKind kind) {
        this.kind = kind;
    }

    public void write(CompoundTag nbt, BlockEntity be, Collection<UUID> ids) {
        List<CompoundTag> live = new ArrayList<>();
        if (be.getLevel() instanceof ServerLevel level) {
            for (UUID id : ids) {
                CompoundTag record = kind.snapshot(level, be, id);
                if (record != null) {
                    live.add(record);
                }
            }
        } else {
            live.addAll(records);
        }
        putList(nbt, RECORDS, live);
        putList(nbt, PENDING, pending);
    }

    // true when a template is being placed, its saved ids point at strands from another world
    public boolean read(CompoundTag nbt, boolean clientPacket) {
        records.clear();
        pending.clear();
        readList(nbt, RECORDS, records);
        readList(nbt, PENDING, pending);
        if (clientPacket || !StrandTemplates.isPlacing()) {
            return false;
        }
        for (CompoundTag record : records) {
            if (!hasPending(record.getUUID("Id"))) {
                pending.add(record);
            }
        }
        records.clear();
        waited = 0;
        return true;
    }

    public boolean hasPending(UUID id) {
        return pending.stream().anyMatch(record -> record.getUUID("Id").equals(id));
    }

    public void dropPending(UUID id) {
        pending.removeIf(record -> record.getUUID("Id").equals(id));
    }

    public void tick(BlockEntity be) {
        if (pending.isEmpty() || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean stuck = false;
        for (CompoundTag record : new ArrayList<>(pending)) {
            BlockEntity far = findFar(level, be, record);
            if (far == null) {
                stuck = true;
                continue;
            }
            pending.remove(record);
            StrandTemplateState farState = kind.stateOf(far);
            if (farState != null) {
                farState.dropPending(record.getUUID("Id"));
            }
            kind.restore(level, be, far, record);
        }
        waited = stuck ? waited + 1 : 0;
        if (waited > GIVE_UP_TICKS) {
            pending.clear();
            waited = 0;
        }
        be.setChanged();
    }

    @Nullable
    private BlockEntity findFar(ServerLevel level, BlockEntity be, CompoundTag record) {
        int[] far = record.getIntArray("Far");
        if (far.length != 3) {
            return null;
        }
        int x = far[0];
        int z = far[2];
        int[][] flips = {{x, z}, {-z, x}, {-x, -z}, {z, -x}, {-x, z}, {x, -z}, {z, x}, {-z, -x}};
        UUID id = record.getUUID("Id");
        String farBlock = record.getString("FarBlock");
        for (int[] flip : flips) {
            BlockPos pos = be.getBlockPos().offset(flip[0], far[1], flip[1]);
            BlockEntity candidate = level.getBlockEntity(pos);
            if (candidate == null || candidate == be
                    || !farBlock.equals(BuiltInRegistries.BLOCK.getKey(candidate.getBlockState().getBlock()).toString())) {
                continue;
            }
            StrandTemplateState state = kind.stateOf(candidate);
            if (state == null ? kind.farMayLackRecord() : state.hasPending(id)) {
                return candidate;
            }
        }
        return null;
    }

    private static void putList(CompoundTag nbt, String key, List<CompoundTag> list) {
        if (list.isEmpty()) {
            return;
        }
        ListTag tag = new ListTag();
        for (CompoundTag record : list) {
            tag.add(record.copy());
        }
        nbt.put(key, tag);
    }

    private static void readList(CompoundTag nbt, String key, List<CompoundTag> out) {
        ListTag tag = nbt.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < tag.size(); i++) {
            out.add(tag.getCompound(i));
        }
    }
}
