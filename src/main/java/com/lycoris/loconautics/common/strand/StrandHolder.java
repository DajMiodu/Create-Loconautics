package com.lycoris.loconautics.common.strand;

import net.minecraft.world.phys.Vec3;

public interface StrandHolder {

    Vec3 getAttachmentPoint();

    default double strandStartTrim() {
        return 0.0;
    }
}
