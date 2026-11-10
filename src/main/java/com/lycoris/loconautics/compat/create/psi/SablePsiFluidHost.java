package com.lycoris.loconautics.compat.create.psi;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public interface SablePsiFluidHost {

    void loconautics$setFluidCapability(@Nullable IFluidHandler handler);
}
