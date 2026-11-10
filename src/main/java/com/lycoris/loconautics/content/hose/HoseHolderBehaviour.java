package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.common.strand.SingleStrandAttachment;
import com.lycoris.loconautics.common.strand.StrandTemplateState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class HoseHolderBehaviour extends BlockEntityBehaviour implements SingleStrandAttachment {

    public static final BehaviourType<HoseHolderBehaviour> TYPE = new BehaviourType<>();

    public final boolean isOwner;

    @Nullable private UUID attachedStrandId;

    private final StrandTemplateState templateState = new StrandTemplateState(HoseTemplateKind.INSTANCE);

    public HoseHolderBehaviour(SmartBlockEntity be, boolean isOwner) {
        super(be);
        this.isOwner = isOwner;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public boolean isFree() {
        if (attachedStrandId == null) {
            return true;
        }
        if (getWorld() == null || getWorld().isClientSide || getAttachedStrand() != null) {
            return false;
        }
        attachedStrandId = null;
        blockEntity.setChanged();
        return true;
    }

    @Nullable
    public UUID getAttachedStrandId() {
        return attachedStrandId;
    }

    @Nullable
    @Override
    public UUID rawAttachedStrandId() {
        return attachedStrandId;
    }

    @Override
    public void rawAttachedStrandId(@Nullable UUID id) {
        this.attachedStrandId = id;
    }

    @Override
    public void markChanged() {
        blockEntity.setChanged();
    }

    @Nullable
    public HoseStrand getAttachedStrand() {
        if (attachedStrandId == null || getWorld() == null) {
            return null;
        }
        HoseStrandManager mgr = HoseStrandManager.get(getWorld());
        return mgr == null ? null : mgr.get(attachedStrandId);
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        writeAttachedStrand(nbt);
        templateState.write(nbt, blockEntity, attachedStrandId == null ? List.of() : List.of(attachedStrandId));
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
        readAttachedStrand(nbt);
        if (templateState.read(nbt, clientPacket)) {
            attachedStrandId = null;
        }
    }
}
