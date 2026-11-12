package com.lycoris.loconautics.common.experimental;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public record ExperimentalCondition(String feature) implements ICondition {

    public static final MapCodec<ExperimentalCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("feature").forGetter(ExperimentalCondition::feature)
    ).apply(instance, ExperimentalCondition::new));

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, LoconauticsConstants.MOD_ID);

    static {
        CONDITIONS.register("experimental", () -> CODEC);
    }

    public static void register(IEventBus modEventBus) {
        CONDITIONS.register(modEventBus);
    }

    @Override
    public boolean test(IContext context) {
        ExperimentalFeature found = ExperimentalFeature.byKey(feature);
        return found != null && found.enabled();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
