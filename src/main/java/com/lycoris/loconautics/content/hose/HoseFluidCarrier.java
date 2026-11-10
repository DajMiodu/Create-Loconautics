package com.lycoris.loconautics.content.hose;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public interface HoseFluidCarrier {

    @Nullable
    IFluidHandler getHoseFluidHandler();
}
