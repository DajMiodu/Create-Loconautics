package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.common.strand.StrandTemplateKind;
import com.lycoris.loconautics.common.strand.StrandTemplateState;
import com.lycoris.loconautics.common.strand.StrandTemplates;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class HoseTemplateKind implements StrandTemplateKind {

    public static final HoseTemplateKind INSTANCE = new HoseTemplateKind();

    private HoseTemplateKind() {
    }

    @Nullable
    @Override
    public CompoundTag snapshot(ServerLevel level, BlockEntity self, UUID strandId) {
        HoseStrandManager strands = HoseStrandManager.get(level);
        HoseStrand strand = strands == null ? null : strands.get(strandId);
        if (strand == null || strand.nozzleLocalPos == null) {
            return null;
        }
        BlockPos pos = self.getBlockPos();
        BlockPos far;
        if (strand.winchPos.equals(pos)) {
            far = strand.nozzleLocalPos;
        } else if (strand.nozzleLocalPos.equals(pos)) {
            far = strand.winchPos;
        } else {
            return null;
        }
        if (!Objects.equals(subIdAt(level, pos), subIdAt(level, far))) {
            return null;
        }
        CompoundTag data = new CompoundTag();
        data.putString("Type", strand.type.id().toString());
        data.putFloat("Length", strand.currentLength);
        data.putBoolean("Locked", strand.locked);
        data.putInt("Fluid", strand.fluidBuffer);
        return StrandTemplates.record(strandId, self, far, data);
    }

    @Override
    public boolean restore(ServerLevel level, BlockEntity self, BlockEntity far, CompoundTag record) {
        HoseStrandManager strands = HoseStrandManager.get(level);
        if (strands == null) {
            return false;
        }
        HoseWinchBlockEntity winch;
        BlockEntity other;
        if (self instanceof HoseWinchBlockEntity w) {
            winch = w;
            other = far;
        } else if (far instanceof HoseWinchBlockEntity w) {
            winch = w;
            other = self;
        } else {
            return false;
        }
        CompoundTag data = record.getCompound("Data");
        HoseStrand strand = new HoseStrand(UUID.randomUUID(), winch.getBlockPos(),
                HoseType.get(ResourceLocation.parse(data.getString("Type"))));
        strand.nozzleSubLevelId = subIdAt(level, other.getBlockPos());
        strand.nozzleLocalPos = other.getBlockPos();
        strand.nozzlePos = other.getBlockPos();
        strand.currentLength = data.getFloat("Length");
        strand.locked = data.getBoolean("Locked");
        strand.fluidBuffer = data.getInt("Fluid");
        strands.create(strand);
        winch.onStrandAttached(strand);
        if (other instanceof HoseStrandHolder nozzle) {
            nozzle.onStrandAttached(strand);
        }
        return true;
    }

    @Nullable
    @Override
    public StrandTemplateState stateOf(BlockEntity be) {
        HoseHolderBehaviour holder = be instanceof SmartBlockEntity smart
                ? smart.getBehaviour(HoseHolderBehaviour.TYPE) : null;
        return holder == null ? null : holder.templateState();
    }

    @Override
    public boolean farMayLackRecord() {
        return true;
    }

    @Nullable
    private static UUID subIdAt(ServerLevel level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        return sub == null ? null : sub.getUniqueId();
    }
}
