package com.lycoris.loconautics.content.cerisene;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

public final class CeriseneFluidType extends FluidType {

    private static final ResourceLocation STILL = LoconauticsConstants.id("block/cerisene/still");
    private static final ResourceLocation FLOWING = LoconauticsConstants.id("block/cerisene/flow");

    public CeriseneFluidType(Properties properties) {
        super(properties);
    }

    public static IClientFluidTypeExtensions clientExtensions() {
        return new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOWING;
            }

            @Override
            public int getTintColor() {
                return CeriseneTuning.FLUID_TINT;
            }
        };
    }
}
