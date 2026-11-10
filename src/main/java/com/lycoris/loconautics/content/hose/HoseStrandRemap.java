package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.common.strand.RopeManager;
import com.lycoris.loconautics.common.strand.StrandAttachments;
import com.lycoris.loconautics.common.strand.StrandEnd;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class HoseStrandRemap implements StrandRemap.Target<HoseStrand> {

    public static final HoseStrandRemap INSTANCE = new HoseStrandRemap();

    private HoseStrandRemap() {
    }

    @Override
    public String name() {
        return "hose winch";
    }

    @Override
    public Collection<HoseStrand> strands(ServerLevel level) {
        HoseStrandManager strands = HoseStrandManager.get(level);
        return strands == null ? List.of() : strands.all();
    }

    @Override
    public UUID idOf(HoseStrand strand) {
        return strand.id;
    }

    @Override
    public boolean remapEnds(HoseStrand strand, StrandEnd from, StrandEnd to) {
        boolean winch = from.local().equals(strand.winchPos);
        if (winch) {
            strand.winchPos = to.local();
        }
        boolean nozzle = from.matches(strand.nozzleSubLevelId, strand.nozzleLocalPos);
        if (nozzle) {
            strand.nozzleSubLevelId = to.subLevelId();
            strand.nozzleLocalPos = to.local();
        }
        return winch || nozzle;
    }

    @Override
    public List<StrandEnd> endsOf(HoseStrand strand) {
        List<StrandEnd> ends = new ArrayList<>(2);
        ends.add(new StrandEnd(null, strand.winchPos));
        if (strand.nozzleLocalPos != null) {
            ends.add(new StrandEnd(strand.nozzleSubLevelId, strand.nozzleLocalPos));
        }
        return ends;
    }

    @Nullable
    @Override
    public RopeManager<?> ropes(ServerLevel level) {
        return HoseRopeManager.get(level);
    }

    @Override
    public void touch(ServerLevel level) {
        HoseStrandManager strands = HoseStrandManager.get(level);
        if (strands != null) {
            strands.touch();
        }
    }

    @Override
    public void handOver(@Nullable BlockEntity from, @Nullable BlockEntity to, UUID strandId) {
        StrandAttachments.handOver(from, to, HoseHolderBehaviour.TYPE, strandId);
    }
}
