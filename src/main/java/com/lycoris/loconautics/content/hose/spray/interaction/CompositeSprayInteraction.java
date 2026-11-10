package com.lycoris.loconautics.content.hose.spray.interaction;

import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntityRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public record CompositeSprayInteraction(@Nullable HoseSprayInteraction base, List<HoseSprayEntityRule> rules,
                                        List<Predicate<EntityType<?>>> blacklist) implements HoseSprayInteraction {

    @Override
    public boolean onBlock(ServerLevel level, BlockPos pos, BlockState state, Direction hitDir,
                           FluidStack fluid, float power) {
        if (base != null) return base.onBlock(level, pos, state, hitDir, fluid, power);
        return !state.isAir();
    }

    @Override
    public void onEntity(ServerLevel level, Entity entity, FluidStack fluid, float power,
                         @Nullable LivingEntity attacker) {
        for (Predicate<EntityType<?>> b : blacklist) {
            if (b.test(entity.getType())) return;
        }
        if (base != null) base.onEntity(level, entity, fluid, power, attacker);
        for (HoseSprayEntityRule rule : rules) {
            if (rule.matches(entity)) {
                rule.action().apply(level, entity, power, attacker);
                break;
            }
        }
    }
}
