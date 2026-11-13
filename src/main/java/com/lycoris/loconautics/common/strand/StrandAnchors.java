package com.lycoris.loconautics.common.strand;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class StrandAnchors {

    private static final double HOSE_NOZZLE_OFFSET = 0.0;

    private StrandAnchors() {
    }

    public static Vec3 hoseNozzle(BlockPos local) {
        return Vec3.atCenterOf(local).add(0.0, HOSE_NOZZLE_OFFSET, 0.0);
    }
}
