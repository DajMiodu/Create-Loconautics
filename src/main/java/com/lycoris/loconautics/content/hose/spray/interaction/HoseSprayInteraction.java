package com.lycoris.loconautics.content.hose.spray.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public interface HoseSprayInteraction {

    default boolean onBlock(ServerLevel level, BlockPos pos, BlockState state, Direction hitDir, FluidStack fluid,
                            float power) {
        return false;
    }

    default void onEntity(ServerLevel level, Entity entity, FluidStack fluid, float power,
                          @Nullable LivingEntity attacker) {
    }
}
