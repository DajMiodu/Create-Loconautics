package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.RopeManager;
import com.lycoris.loconautics.common.strand.StrandEnd;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRopeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ScrewLinkStrandRemap implements StrandRemap.Target<ScrewLinkStrand> {

    public static final ScrewLinkStrandRemap INSTANCE = new ScrewLinkStrandRemap();

    private ScrewLinkStrandRemap() {
    }

    @Override
    public String name() {
        return "screw link";
    }

    @Override
    public Collection<ScrewLinkStrand> strands(ServerLevel level) {
        ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(level);
        return strands == null ? List.of() : strands.all();
    }

    @Override
    public UUID idOf(ScrewLinkStrand strand) {
        return strand.id;
    }

    @Override
    public boolean remapEnds(ScrewLinkStrand strand, StrandEnd from, StrandEnd to) {
        boolean a = from.matches(strand.subA, strand.localA);
        if (a) {
            strand.subA = to.subLevelId();
            strand.localA = to.local();
        }
        boolean b = strand.isCoupled() && from.matches(strand.subB, strand.localB);
        if (b) {
            strand.subB = to.subLevelId();
            strand.localB = to.local();
        }
        return a || b;
    }

    @Override
    public List<StrandEnd> endsOf(ScrewLinkStrand strand) {
        List<StrandEnd> ends = new ArrayList<>(2);
        ends.add(new StrandEnd(strand.subA, strand.localA));
        if (strand.isCoupled()) {
            ends.add(new StrandEnd(strand.subB, strand.localB));
        }
        return ends;
    }

    @Nullable
    @Override
    public RopeManager<?> ropes(ServerLevel level) {
        return ScrewLinkRopeManager.get(level);
    }

    @Override
    public void touch(ServerLevel level) {
        ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(level);
        if (strands != null) {
            strands.touch();
        }
    }

    @Override
    public void handOver(@Nullable BlockEntity from, @Nullable BlockEntity to, UUID strandId) {
    }
}
