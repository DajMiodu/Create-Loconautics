package com.lycoris.loconautics.content.hose.spray.interaction;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class HoseSprayInteractions {

    private static final Map<Fluid, HoseSprayInteraction> BY_FLUID = new HashMap<>();
    private static final List<Map.Entry<TagKey<Fluid>, HoseSprayInteraction>> BY_TAG = new ArrayList<>();
    private static final List<Map.Entry<Predicate<Fluid>, HoseSprayInteraction>> BY_PREDICATE = new ArrayList<>();

    private static final Map<String, HoseSprayInteraction> EFFECTS = new HashMap<>();

    private static Runnable builtin = () -> {};

    private HoseSprayInteractions() {
    }

    public static void setBuiltin(Runnable r) {
        builtin = r;
    }

    public static void registerEffect(String id, HoseSprayInteraction effect) {
        EFFECTS.put(id, effect);
    }

    @Nullable
    public static HoseSprayInteraction effect(String id) {
        return EFFECTS.get(id);
    }

    public static java.util.Set<String> effectIds() {
        return EFFECTS.keySet();
    }

    public static synchronized void rebuild(Runnable datapack) {
        BY_FLUID.clear();
        BY_TAG.clear();
        BY_PREDICATE.clear();
        builtin.run();
        datapack.run();
    }

    public static Fluid sourceOf(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    public static void register(Fluid fluid, HoseSprayInteraction interaction) {
        BY_FLUID.put(sourceOf(fluid), interaction);
    }

    public static void register(TagKey<Fluid> tag, HoseSprayInteraction interaction) {
        BY_TAG.add(Map.entry(tag, interaction));
    }

    public static void register(Predicate<Fluid> match, HoseSprayInteraction interaction) {
        BY_PREDICATE.add(Map.entry(match, interaction));
    }

    @Nullable
    public static HoseSprayInteraction get(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Fluid fluid = sourceOf(stack.getFluid());
        HoseSprayInteraction exact = BY_FLUID.get(fluid);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<TagKey<Fluid>, HoseSprayInteraction> e : BY_TAG) {
            if (fluid.builtInRegistryHolder().is(e.getKey())) {
                return e.getValue();
            }
        }
        for (Map.Entry<Predicate<Fluid>, HoseSprayInteraction> e : BY_PREDICATE) {
            if (e.getKey().test(fluid)) {
                return e.getValue();
            }
        }
        return null;
    }
}
