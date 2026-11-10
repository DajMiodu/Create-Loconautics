package com.lycoris.loconautics.foundation.smoke;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SmokeOptions(int color, SmokeStyle style, float scale, float lift) implements ParticleOptions {

    public SmokeOptions(int color, SmokeStyle style) {
        this(color, style, 1.0F, 1.0F);
    }

    public SmokeOptions(int color, SmokeStyle style, float scale) {
        this(color, style, scale, 1.0F);
    }

    private static final int EXHAUST_LIGHT = 0xC8;
    private static final int EXHAUST_HEAVY = 0x5A;

    // hazy light gray at a light load, darker as the engine burns more
    public static SmokeOptions exhaust(float load) {
        int shade = Math.round(EXHAUST_LIGHT + (EXHAUST_HEAVY - EXHAUST_LIGHT) * Math.max(0F, Math.min(1F, load)));
        return new SmokeOptions((shade << 16) | (shade << 8) | shade, SmokeStyle.EXHAUST);
    }

    public static MapCodec<SmokeOptions> codec(ParticleType<SmokeOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("color").forGetter(SmokeOptions::color),
                Codec.INT.fieldOf("style").forGetter(o -> o.style().ordinal()),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(SmokeOptions::scale),
                Codec.FLOAT.optionalFieldOf("lift", 1.0F).forGetter(SmokeOptions::lift)
        ).apply(i, (color, style, scale, lift) -> new SmokeOptions(color, SmokeStyle.byId(style), scale, lift)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, SmokeOptions> streamCodec(ParticleType<SmokeOptions> t) {
        return StreamCodec.composite(
                ByteBufCodecs.INT, SmokeOptions::color,
                ByteBufCodecs.VAR_INT, o -> o.style().ordinal(),
                ByteBufCodecs.FLOAT, SmokeOptions::scale,
                ByteBufCodecs.FLOAT, SmokeOptions::lift,
                (color, style, scale, lift) -> new SmokeOptions(color, SmokeStyle.byId(style), scale, lift));
    }

    @Override
    public ParticleType<?> getType() {
        return LoconauticsRegistries.SMOKE.get();
    }
}
