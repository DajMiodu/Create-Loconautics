package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.StrandAttachmentStore;
import com.lycoris.loconautics.common.strand.StrandTemplateState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SteelCableHolderBehaviour extends BlockEntityBehaviour
        implements StrandAttachmentStore {

    public static final BehaviourType<SteelCableHolderBehaviour> TYPE = new BehaviourType<>();

    private final List<UUID> attached = new ArrayList<>();
    private final int capacity;
    private final StrandTemplateState templateState = new StrandTemplateState(SteelCableTemplateKind.INSTANCE);

    public SteelCableHolderBehaviour(SmartBlockEntity be, int capacity) {
        super(be);
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public List<UUID> attachedStrandIds() {
        return attached;
    }

    public void attach(UUID id) {
        if (!attached.contains(id)) {
            attached.add(id);
            blockEntity.setChanged();
        }
    }

    public void detach(UUID id) {
        if (attached.remove(id)) {
            blockEntity.setChanged();
        }
    }

    public boolean holds(UUID id) {
        pruneDead();
        return attached.contains(id);
    }

    public boolean isFree() {
        pruneDead();
        return attached.size() < capacity;
    }

    // a strand torn down while this end was unloaded can never clear its id here, so a dead id heals on the next read
    private void pruneDead() {
        if (attached.isEmpty() || getWorld() == null) {
            return;
        }
        SteelCableStrandManager mgr = SteelCableStrandManager.get(getWorld());
        if (mgr == null) {
            return;
        }
        if (attached.removeIf(id -> mgr.get(id) == null)) {
            blockEntity.setChanged();
        }
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        ListTag list = new ListTag();
        for (UUID id : attached) {
            list.add(NbtUtils.createUUID(id));
        }
        nbt.put("AttachedStrands", list);
        templateState.write(nbt, blockEntity, attached);
    }

    @Override
    public void tick() {
        super.tick();
        templateState.tick(blockEntity);
    }

    public StrandTemplateState templateState() {
        return templateState;
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        attached.clear();
        ListTag list = nbt.getList("AttachedStrands", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            attached.add(NbtUtils.loadUUID(list.get(i)));
        }
        if (templateState.read(nbt, clientPacket)) {
            attached.clear();
        }
    }
}
