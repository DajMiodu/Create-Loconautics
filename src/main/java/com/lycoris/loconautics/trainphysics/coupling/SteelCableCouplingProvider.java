package com.lycoris.loconautics.trainphysics.coupling;

import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.function.Consumer;

public final class SteelCableCouplingProvider implements CouplingProvider {

    @Override
    public void collectCoupledSubLevels(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours) {
        SteelCableStrandManager manager = SteelCableStrandManager.get(level);
        if (manager == null) {
            return;
        }
        for (SteelCableStrand strand : manager.all()) {
            UUID a = strand.subA;
            UUID b = strand.subB;
            if (a == null || b == null) {
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
