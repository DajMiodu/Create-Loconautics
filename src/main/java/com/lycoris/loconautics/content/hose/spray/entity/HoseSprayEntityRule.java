package com.lycoris.loconautics.content.hose.spray.entity;

import com.google.gson.JsonObject;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.spray.HoseSprayDamage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public record HoseSprayEntityRule(List<Predicate<EntityType<?>>> match, Action action) {

    public interface Action {
        void apply(ServerLevel level, Entity entity, float power, @Nullable LivingEntity attacker);
    }

    public boolean matches(Entity entity) {
        if (match.isEmpty()) return true;
        for (Predicate<EntityType<?>> p : match) {
            if (p.test(entity.getType())) return true;
        }
        return false;
    }

    public static Predicate<EntityType<?>> matcher(String s) {
        if (s.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(s.substring(1)));
            return type -> type.builtInRegistryHolder().is(tag);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(s)).orElse(null);
        if (type == null) {
            LoconauticsConstants.LOGGER.error("[HoseSpray] unknown entity type {}", s);
        }
        return t -> t == type;
    }

    public static List<Predicate<EntityType<?>>> matchers(com.google.gson.JsonArray arr) {
        List<Predicate<EntityType<?>>> out = new ArrayList<>();
        if (arr != null) {
            for (var e : arr) out.add(matcher(e.getAsString()));
        }
        return out;
    }

    public static HoseSprayEntityRule fromJson(JsonObject o) {
        List<Predicate<EntityType<?>>> match =
                matchers(o.has("match") ? o.getAsJsonArray("match") : null);

        if (o.has("mob_effect")) {
            ResourceLocation id = ResourceLocation.parse(o.get("mob_effect").getAsString());
            int dur = o.has("duration") ? o.get("duration").getAsInt() : 200;
            int amp = o.has("amplifier") ? o.get("amplifier").getAsInt() : 0;
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(
                    net.minecraft.resources.ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
            if (holder == null) {
                LoconauticsConstants.LOGGER.error("[HoseSpray] unknown mob_effect {}", id);
                return new HoseSprayEntityRule(match, (l, e, p, who) -> {});
            }
            Action a = (level, entity, power, attacker) -> {
                if (entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(holder, dur, amp));
                }
            };
            return new HoseSprayEntityRule(match, a);
        }
        if (o.has("damage")) {
            float amount = o.get("damage").getAsFloat();
            Action a = (level, entity, power, attacker) -> entity.hurt(
                    HoseSprayDamage.of(level, HoseSprayDamage.Substance.UNKNOWN, attacker), amount);
            return new HoseSprayEntityRule(match, a);
        }
        if (o.has("ignite")) {
            float seconds = o.get("ignite").getAsFloat();
            Action a = (level, entity, power, attacker) -> entity.setRemainingFireTicks((int) (seconds * 20));
            return new HoseSprayEntityRule(match, a);
        }
        return new HoseSprayEntityRule(match, (l, e, p, who) -> {});
    }
}
