package com.lycoris.loconautics.compat.heat;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class PropulsionEngineHeat {

    private static final String MOD_ID = "createpropulsion";
    static final float CAPACITY = 600f;
    static final float PER_TICK = 2f;
    static final float PASSIVE_LOSS = 0.05f;

    private PropulsionEngineHeat() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static BlockEntityBehaviour create(SmartBlockEntity engine) {
        return PropulsionHeatImpl.create(engine);
    }

    public static float perTick() {
        return PER_TICK;
    }

    public static void tick(BlockEntityBehaviour behavior, Level level, BlockPos pos, float generation) {
        PropulsionHeatImpl.tick(behavior, level, pos, generation);
    }
}
