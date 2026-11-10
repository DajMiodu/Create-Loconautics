package com.lycoris.loconautics.compat.create.patch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ActorLockScroll extends ScrollValueBehaviour {

    public ActorLockScroll(SmartBlockEntity be) {
        super(Component.translatable("loconautics.actor_lock.label"), be, NoHitSlot.INSTANCE);
        between(0, ActorLockMode.values().length - 1);
    }

    public ActorLockMode getMode() {
        return ActorLockMode.values()[getValue()];
    }

    private static final class NoHitSlot extends ValueBoxTransform {

        static final NoHitSlot INSTANCE = new NoHitSlot();

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return null;
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {}

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            return false;
        }
    }
}