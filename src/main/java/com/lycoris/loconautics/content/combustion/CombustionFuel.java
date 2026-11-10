package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.content.cerisene.CeriseneBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public enum CombustionFuel {

    CERISENE(null, 256f, 1.0f / 20f),
    DIESEL("diesel", 240f, 1.2f / 20f),
    GASOLINE("gasoline", 224f, 1.5f / 20f),
    REFINED_FUEL("refined_fuel", 208f, 1.8f / 20f),
    BIODIESEL("biodiesel", 192f, 2.1f / 20f),
    HEAVY_OIL("heavy_oil", 176f, 2.4f / 20f),
    ETHANOL("ethanol", 160f, 3.0f / 20f),
    LAVA(null, 128f, 6.0f / 20f);

    private final @Nullable TagKey<Fluid> tag;
    private final float topRpm;
    private final float burnPerTick;

    CombustionFuel(@Nullable String tagPath, float topRpm, float burnPerTick) {
        this.tag = tagPath == null ? null : TagKey.create(Registries.FLUID,
                ResourceLocation.fromNamespaceAndPath("c", tagPath));
        this.topRpm = topRpm;
        this.burnPerTick = burnPerTick;
    }

    public float topRpm() {
        return topRpm;
    }

    public float burnPerTick() {
        return burnPerTick;
    }

    @Nullable
    public static CombustionFuel of(FluidStack stack) {
        return stack.isEmpty() ? null : of(stack.getFluid());
    }

    @Nullable
    public static CombustionFuel of(Fluid fluid) {
        if (fluid.isSame(CeriseneBlocks.CERISENE.get())) return CERISENE;
        if (fluid.isSame(Fluids.LAVA)) return LAVA;
        for (CombustionFuel fuel : values()) {
            if (fuel.tag != null && fluid.defaultFluidState().is(fuel.tag)) return fuel;
        }
        return null;
    }

    public static boolean accepts(FluidStack stack) {
        return of(stack) != null;
    }
}
