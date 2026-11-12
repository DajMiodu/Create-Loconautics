package com.lycoris.loconautics.foundation.smoke;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.particle.LateParticles;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SmokeGovernor {

    private static final long STEP_NANOS = 1_000_000_000L;
    private static final float MIN_SCALE = 0.25F;
    private static final int MIN_LIVE = 200;
    private static final float CUT = 0.85F;
    private static final float REGAIN = 1.1F;
    private static final float REGAIN_MARGIN = 1.25F;

    private static float scale = 1.0F;
    private static long lastStep;

    private SmokeGovernor() {
    }

    public static float scale() {
        if (!LoconauticsConfig.SMOKESTACK_ADAPTIVE.get()) {
            scale = 1.0F;
            return scale;
        }
        long now = System.nanoTime();
        if (now - lastStep >= STEP_NANOS) {
            lastStep = now;
            adjust();
        }
        return scale;
    }

    private static void adjust() {
        int target = LoconauticsConfig.SMOKESTACK_TARGET_FPS.get();
        int fps = Minecraft.getInstance().getFps();
        if (fps < target && LateParticles.liveCount() > MIN_LIVE) {
            scale = Math.max(MIN_SCALE, scale * CUT);
        } else if (fps > target * REGAIN_MARGIN) {
            scale = Math.min(1.0F, scale * REGAIN);
        }
    }
}
