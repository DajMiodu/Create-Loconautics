package com.lycoris.loconautics.content.hose.spray;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class HoseSprayDamage extends DamageSource {

    public enum Substance {
        UNKNOWN,
        LAVA
    }

    private static final ResourceKey<DamageType> TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, LoconauticsConstants.id("hose_spray"));
    private static final String KEY = "death.attack.loconautics.hose_spray";
    private static final float RARE_CHANCE = 0.05f;

    private final String key;

    private HoseSprayDamage(Holder<DamageType> type, @Nullable Entity attacker, String key) {
        super(type, null, attacker);
        this.key = key;
    }

    public static DamageSource of(ServerLevel level, Substance substance, @Nullable LivingEntity attacker) {
        String key = substance == Substance.LAVA ? KEY + ".lava" : KEY;
        if (level.random.nextFloat() < RARE_CHANCE) {
            key += ".rare";
        } else if (attacker != null) {
            key += ".player";
        }
        return new HoseSprayDamage(level.damageSources().source(TYPE).typeHolder(), attacker, key);
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity victim) {
        Entity attacker = getEntity();
        if (attacker == null) {
            return Component.translatable(key, victim.getDisplayName());
        }
        return Component.translatable(key, victim.getDisplayName(), attacker.getDisplayName());
    }
}
