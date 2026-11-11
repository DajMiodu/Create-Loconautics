package com.lycoris.loconautics.foundation;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.combustion.SmallEngineBlocks;
import com.lycoris.loconautics.content.combustion.SmallEngineTuning;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.api.stress.BlockStressValues;

public final class LoconauticsStress {

    private LoconauticsStress() {
    }

    public static void register() {
        BlockStressValues.IMPACTS.register(LoconauticsRegistries.BEARING_AXLE.get(), LoconauticsConfig.BASE_IMPACT::get);
        BlockStressValues.IMPACTS.register(LoconauticsRegistries.TRANSMISSION.get(),
                LoconauticsConfig.TRANSMISSION_SU_IMPACT::get);
        BlockStressValues.IMPACTS.register(LoconauticsRegistries.TURNTABLE_BEARING.get(),
                LoconauticsConfig.TURNTABLE_SU_IMPACT::get);

        BlockStressValues.CAPACITIES.register(BoilerBlocks.STEAM_TAKEOFF.get(),
                LoconauticsConfig.SU_PER_BODY_BLOCK::get);
        BlockStressValues.CAPACITIES.register(LoconauticsRegistries.HOSE_WINCH.get(),
                () -> HoseWinchBlockEntity.FREE_SPIN_CAPACITY);
        SmallEngineBlocks.ENGINES.values().forEach(engine -> {
            BlockStressValues.CAPACITIES.register(engine.get(), () -> SmallEngineTuning.SU_PER_RPM);
            BlockStressValues.RPM.register(engine.get(),
                    new BlockStressValues.GeneratedRpm((int) SmallEngineTuning.TOP_RPM, true));
        });
    }
}
