package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.StrandTemplateKind;
import com.lycoris.loconautics.common.strand.StrandTemplateState;
import com.lycoris.loconautics.common.strand.StrandTemplates;
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

public final class SteelCableTemplateKind implements StrandTemplateKind {

    public static final SteelCableTemplateKind INSTANCE = new SteelCableTemplateKind();

    private SteelCableTemplateKind() {
    }

    @Nullable
    @Override
    public CompoundTag snapshot(ServerLevel level, BlockEntity self, UUID strandId) {
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        SteelCableStrand strand = strands == null ? null : strands.get(strandId);
        if (strand == null || !Objects.equals(strand.subA, strand.subB)) {
            return null;
        }
        boolean selfIsA = strand.localA.equals(self.getBlockPos());
        BlockPos far = selfIsA ? strand.localB : strand.localA;
        if (far.equals(self.getBlockPos())) {
            return null;
        }
        CompoundTag data = new CompoundTag();
        data.putString("Type", strand.type.id().toString());
        data.putDouble("RestLength", strand.restLength);
        data.putBoolean("SelfIsA", selfIsA);
        return StrandTemplates.record(strandId, self, far, data);
    }

    @Override
    public boolean restore(ServerLevel level, BlockEntity self, BlockEntity far, CompoundTag record) {
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        if (strands == null || !(self instanceof SteelCableHolder holderSelf)
                || !(far instanceof SteelCableHolder holderFar)) {
            return false;
        }
        CompoundTag data = record.getCompound("Data");
        boolean selfIsA = data.getBoolean("SelfIsA");
        BlockEntity a = selfIsA ? self : far;
        BlockEntity b = selfIsA ? far : self;
        SteelCableStrand strand = new SteelCableStrand(UUID.randomUUID(), subIdAt(level, a.getBlockPos()),
                a.getBlockPos(), subIdAt(level, b.getBlockPos()), b.getBlockPos(),
                SteelCableType.get(ResourceLocation.parse(data.getString("Type"))));
        strand.restLength = data.getDouble("RestLength");
        strands.create(strand);
        holderSelf.onStrandAttached(strand);
        holderFar.onStrandAttached(strand);
        return true;
    }

    @Nullable
    @Override
    public StrandTemplateState stateOf(BlockEntity be) {
        SteelCableHolderBehaviour holder = be instanceof SmartBlockEntity smart
                ? smart.getBehaviour(SteelCableHolderBehaviour.TYPE) : null;
        return holder == null ? null : holder.templateState();
    }

    @Nullable
    private static UUID subIdAt(ServerLevel level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        return sub == null ? null : sub.getUniqueId();
    }
}
