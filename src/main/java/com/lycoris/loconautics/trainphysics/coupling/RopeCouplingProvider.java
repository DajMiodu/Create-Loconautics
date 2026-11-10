package com.lycoris.loconautics.trainphysics.coupling;

import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.function.Consumer;

public final class RopeCouplingProvider implements CouplingProvider {

    @Override
    public void collectCoupledSubLevels(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours) {
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
        if (manager == null) {
            return;
        }
        for (ServerRopeStrand strand : manager.getAllStrands()) {
            UUID a = null;
            UUID b = null;
            int seen = 0;
            for (RopeAttachment attachment : strand.getAttachments()) {
                if (seen == 0) {
                    a = attachment.subLevelID();
                } else {
                    b = attachment.subLevelID();
                }
                seen++;
            }
            if (seen < 2) {
                continue;
            }
            if (subLevelId.equals(a) && b != null && !subLevelId.equals(b)) {
                neighbours.accept(b);
            } else if (subLevelId.equals(b) && a != null && !subLevelId.equals(a)) {
                neighbours.accept(a);
            }
        }
    }
}
