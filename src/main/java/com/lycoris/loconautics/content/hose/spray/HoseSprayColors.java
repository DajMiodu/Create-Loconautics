package com.lycoris.loconautics.content.hose.spray;

import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class HoseSprayColors {

    private static final Map<Fluid, int[]> OVERRIDES = new HashMap<>();

    private HoseSprayColors() {
    }

    public static void setDatapack(Map<ResourceLocation, int[]> byFluidId) {
        OVERRIDES.clear();
        for (var e : byFluidId.entrySet()) {
            BuiltInRegistries.FLUID.getOptional(e.getKey()).ifPresent(f -> OVERRIDES.put(HoseSprayInteractions.sourceOf(f), e.getValue()));
        }
    }

    @Nullable
    public static int[] get(Fluid fluid) {
        return OVERRIDES.get(HoseSprayInteractions.sourceOf(fluid));
    }
}
