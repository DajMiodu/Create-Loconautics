package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.RopeManager;
import com.lycoris.loconautics.common.strand.StrandAttachments;
import com.lycoris.loconautics.common.strand.StrandEnd;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class SteelCableStrandRemap implements StrandRemap.Target<SteelCableStrand> {

    public static final SteelCableStrandRemap INSTANCE = new SteelCableStrandRemap();

    private SteelCableStrandRemap() {
    }

    @Override
    public String name() {
        return "steel cable";
    }

    @Override
    public Collection<SteelCableStrand> strands(ServerLevel level) {
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        return strands == null ? List.of() : strands.all();
    }

    @Override
    public UUID idOf(SteelCableStrand strand) {
        return strand.id;
    }

    @Override
    public boolean remapEnds(SteelCableStrand strand, StrandEnd from, StrandEnd to) {
        boolean a = from.matches(strand.subA, strand.localA);
        if (a) {
            strand.subA = to.subLevelId();
            strand.localA = to.local();
        }
        boolean b = from.matches(strand.subB, strand.localB);
        if (b) {
            strand.subB = to.subLevelId();
            strand.localB = to.local();
        }
        return a || b;
    }

    @Override
    public List<StrandEnd> endsOf(SteelCableStrand strand) {
        return List.of(new StrandEnd(strand.subA, strand.localA), new StrandEnd(strand.subB, strand.localB));
    }

    @Nullable
    @Override
    public RopeManager<?> ropes(ServerLevel level) {
        return SteelCableRopeManager.get(level);
    }

    @Override
    public void touch(ServerLevel level) {
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        if (strands != null) {
            strands.touch();
        }
    }

    @Override
    public void handOver(@Nullable BlockEntity from, @Nullable BlockEntity to, UUID strandId) {
        StrandAttachments.detach(from, SteelCableHolderBehaviour.TYPE, strandId);
        StrandAttachments.attach(to, SteelCableHolderBehaviour.TYPE, strandId);
    }
}
