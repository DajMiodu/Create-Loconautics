package com.lycoris.loconautics.compat.heat;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.propulsionteam.propulsionsimulated.content.heat.HeatSourceBehavior;
import dev.propulsionteam.propulsionsimulated.content.heat.IHeatConsumer;
import dev.propulsionteam.propulsionsimulated.content.heat.IHeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

public final class PropulsionEngineHeat {

    private static final String MOD_ID = "createpropulsion";
    private static final float CAPACITY = 600f;
    private static final float PER_TICK = 2f;
    private static final float PASSIVE_LOSS = 0.05f;

    private PropulsionEngineHeat() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static HeatSourceBehavior create(SmartBlockEntity engine) {
        return new HeatSourceBehavior(engine, CAPACITY, PER_TICK);
    }

    public static float perTick() {
        return PER_TICK;
    }

    public static void tick(BlockEntityBehaviour behavior, Level level, BlockPos pos, float generation) {
        if (!(behavior instanceof HeatSourceBehavior source)) return;
        IHeatSource buffer = source.getHeatSource();
        float consumed = offer(buffer, level, pos);
        float loss = consumed == 0f && buffer.getHeatStored() > 0f ? PASSIVE_LOSS : 0f;
        float net = generation - loss - consumed;
        if (net > 0f) {
            buffer.generateHeat(net);
        } else if (net < 0f) {
            buffer.extractHeat(-net, false);
        }
    }

    private static float offer(IHeatSource buffer, Level level, BlockPos pos) {
        BlockEntity above = level.getBlockEntity(pos.above());
        if (!(above instanceof IHeatConsumer consumer) || !consumer.isActive()) return 0f;
        float stored = buffer.getHeatStored();
        if (stored <= 0f || stored < CAPACITY * consumer.getOperatingThreshold()) return 0f;
        return consumer.consumeHeat(stored, buffer.getExpectedHeatProduction(), false);
    }
}
