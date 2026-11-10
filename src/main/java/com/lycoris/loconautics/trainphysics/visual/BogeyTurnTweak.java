package com.lycoris.loconautics.trainphysics.visual;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BogeyTurnTweak {

    private static float gain;


    private BogeyTurnTweak() {
    }

    public static float gain() {
        return gain;
    }

    public static float add(float delta) {
        gain += delta;
        return gain;
    }
}
