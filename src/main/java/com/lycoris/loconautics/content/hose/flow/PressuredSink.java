package com.lycoris.loconautics.content.hose.flow;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public interface PressuredSink extends IFluidHandler {

    int fill(FluidStack resource, float strength, FluidAction action);

    void pull(float strength);

    @Nullable
    default IFluidHandler endTank() {
        return null;
    }
}
