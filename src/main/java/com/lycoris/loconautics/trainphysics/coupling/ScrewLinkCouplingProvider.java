package com.lycoris.loconautics.trainphysics.coupling;

import com.lycoris.loconautics.content.screwlink.ScrewLinkStrand;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrandManager;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.function.Consumer;

public final class ScrewLinkCouplingProvider implements CouplingProvider {

    @Override
    public void collectCoupledSubLevels(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours) {
        ScrewLinkStrandManager manager = ScrewLinkStrandManager.get(level);
        if (manager == null) {
            return;
        }
        for (ScrewLinkStrand strand : manager.all()) {
            UUID a = strand.subA;
            UUID b = strand.subB;
            if (!strand.isCoupled() || a == null || b == null) {
                continue;
            }
            if (subLevelId.equals(a) && !subLevelId.equals(b)) {
                neighbours.accept(b);
            } else if (subLevelId.equals(b) && !subLevelId.equals(a)) {
                neighbours.accept(a);
            }
        }
    }
}
