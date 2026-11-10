package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public interface HoseLinkEndpoint extends SteelCableHolder {

    @Nullable
    IFluidHandler linkSink();

    @Override
    default boolean acceptsFluidLinks() {
        return true;
    }

    @Override
    default boolean acceptsPlainCables() {
        return false;
    }
}
