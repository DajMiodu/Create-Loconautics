package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.StrandHolder;
import com.lycoris.loconautics.common.strand.StrandTemplates;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ScrewLinkBlockEntity extends SmartBlockEntity implements StrandHolder {

    private static final Vec3 ANCHOR_NORTH = new Vec3(0.5, 12.0 / 16.0, 8.0 / 16.0);

    @Nullable private UUID strandId;

    public ScrewLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(level);
        if (strands == null || strandId != null && strands.get(strandId) != null) {
            return;
        }
        SubLevel sub = Sable.HELPER.getContaining(this);
        ScrewLinkStrand strand = strands.create(new ScrewLinkStrand(
                UUID.randomUUID(), sub == null ? null : sub.getUniqueId(), getBlockPos()));
        setStrandId(strand.id);
    }

    @Nullable
    public UUID getStrandId() {
        return strandId;
    }

    public void setStrandId(@Nullable UUID id) {
        strandId = id;
        setChanged();
    }

    @Override
    public Vec3 getAttachmentPoint() {
        Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
        Vec3 local = rotateToFacing(ANCHOR_NORTH, facing);
        return Vec3.atLowerCornerOf(getBlockPos()).add(local);
    }

    public boolean decouple() {
        if (level == null || strandId == null) {
            return false;
        }
        ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(level);
        if (strands == null) {
            return false;
        }
        ScrewLinkStrand strand = strands.get(strandId);
        if (strand == null || !strand.isCoupled()) {
            return false;
        }
        strand.subB = null;
        strand.localB = null;
        strands.touch();
        return true;
    }

    private static Vec3 rotateToFacing(Vec3 north, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(1.0 - north.z, north.y, north.x);
            case SOUTH -> new Vec3(1.0 - north.x, north.y, 1.0 - north.z);
            case WEST -> new Vec3(north.z, north.y, 1.0 - north.x);
            default -> north;
        };
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (strandId != null) {
            tag.putUUID("Strand", strandId);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        strandId = tag.hasUUID("Strand") && (clientPacket || !StrandTemplates.isPlacing())
                ? tag.getUUID("Strand") : null;
    }
}
