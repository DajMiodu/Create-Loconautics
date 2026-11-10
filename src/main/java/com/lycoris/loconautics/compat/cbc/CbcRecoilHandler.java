package com.lycoris.loconautics.compat.cbc;

import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

public final class CbcRecoilHandler {


    private CbcRecoilHandler() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof AbstractCannonProjectile projectile) {
            CbcTrainCompat.onShellFired(event.getLevel(), projectile);
        }
    }
}
