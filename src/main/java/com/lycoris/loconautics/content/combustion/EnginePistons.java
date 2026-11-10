package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public final class EnginePistons {

    private static final float SHAKE_SLOW = 0.4f / 16f;
    private static final float SHAKE_FAST = 0.12f / 16f;
    private static final float TOP_RPM = 256f;
    private static final float FADE_RPM = 8f;
    private static final float WIDE_SHARE = 0.8125f;
    private static final float MID_SHARE = 0.375f;
    private static final float TIGHT_SHARE = 0.125f;
    private static final float MID_SIZE = 0.45f;
    private static final float TIGHT_SIZE = 0.1f;

    private EnginePistons() {}

    public static PartialModel model(BlockState state) {
        return LoconauticsPartialModels.ENGINE_PISTONS.get(((SmallEngineBlock) state.getBlock()).getColor());
    }

    public static float yaw(BlockState state) {
        return 180.0f - state.getValue(SmallEngineBlock.HORIZONTAL_FACING).toYRot();
    }

    // at a standstill the shake is zero so the pistons rest exactly where the model puts them
    // two thumps per turn, a big slow chug at low rpm that shrinks to a fine buzz at top rpm
    // past redstone 3 the shake shrinks, past 10 it shrinks much more, and it flattens into a line so it never looks like orbiting
    public static Vector3f shake(float angle, float seed, float rpm, float share) {
        float tighten = Mth.clamp((share - TIGHT_SHARE) / (WIDE_SHARE - TIGHT_SHARE), 0f, 1f);
        float scale = share >= MID_SHARE
                ? Mth.lerp(Mth.clamp((share - MID_SHARE) / (WIDE_SHARE - MID_SHARE), 0f, 1f), MID_SIZE, 1f)
                : Mth.lerp(Mth.clamp((share - TIGHT_SHARE) / (MID_SHARE - TIGHT_SHARE), 0f, 1f), TIGHT_SIZE, MID_SIZE);
        float size = Mth.lerp(Mth.clamp(rpm / TOP_RPM, 0f, 1f), SHAKE_SLOW, SHAKE_FAST)
                * Mth.clamp(rpm / FADE_RPM, 0f, 1f) * scale;
        float roundness = 1.2f * tighten;
        float thump = Mth.sin(angle * 2f + seed);
        return new Vector3f(thump * size * 0.4f, Mth.sin(angle * 2f + seed + roundness) * size, 0f);
    }
}
