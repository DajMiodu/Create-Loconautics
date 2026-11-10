package com.lycoris.loconautics.content.hose.spray.interaction;

import com.lycoris.loconautics.content.hose.spray.HoseSprayDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class VanillaHoseSprayInteractions {

    private VanillaHoseSprayInteractions() {
    }

    public static final HoseSprayInteraction DOUSE = new HoseSprayInteraction() {
        @Override
        public boolean onBlock(ServerLevel level, BlockPos pos, BlockState state, Direction hitDir,
                               FluidStack fluid, float power) {
            if (state.getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.6f);
                return true;
            }
            if (BurntHoseSprayCompat.LOADED && BurntHoseSprayCompat.isBurntBlock(state)) {
                BurntHoseSprayCompat.douse(level, pos);
                return true;
            }
            if (state.is(Blocks.FARMLAND)) {
                if (state.getValue(FarmBlock.MOISTURE) < 7) {
                    level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 7), 3);
                }
                return true;
            }
            return !state.isAir();
        }

        @Override
        public void onEntity(ServerLevel level, Entity entity, FluidStack fluid, float power,
                             @Nullable LivingEntity attacker) {
            if (entity.isOnFire()) {
                entity.setRemainingFireTicks(0);
            }
        }
    };

    public static final HoseSprayInteraction IGNITE = new HoseSprayInteraction() {
        @Override
        public boolean onBlock(ServerLevel level, BlockPos pos, BlockState state, Direction hitDir,
                               FluidStack fluid, float power) {
            if (state.getBlock() instanceof BaseFireBlock) {
                return true;
            }
            if (state.isAir()) {
                return false;
            }
            BlockPos firePos = pos.relative(hitDir.getOpposite());
            if (level.getBlockState(firePos).isAir()
                    && BaseFireBlock.canBePlacedAt(level, firePos, Direction.UP)) {
                level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 3);
            }
            return true;
        }

        @Override
        public void onEntity(ServerLevel level, Entity entity, FluidStack fluid, float power,
                             @Nullable LivingEntity attacker) {
            entity.setRemainingFireTicks((int) ((3.0f + power * 4.0f) * 20.0f));
            entity.hurt(HoseSprayDamage.of(level, HoseSprayDamage.Substance.LAVA, attacker), 1.0f);
        }
    };

    public static void register() {
        HoseSprayInteractions.registerEffect("douse", DOUSE);
        HoseSprayInteractions.registerEffect("ignite", IGNITE);

        HoseSprayInteractions.register(FluidTags.WATER, DOUSE);
        HoseSprayInteractions.register(FluidTags.LAVA, IGNITE);

        registerPotion();
        registerHoney();
    }

    private static void registerHoney() {
        HoseSprayInteractions.register(
                f -> {
                    net.minecraft.resources.ResourceLocation id =
                            net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f);
                    return id.getNamespace().equals("create") && id.getPath().contains("honey");
                },
                new HoseSprayInteraction() {
                    @Override
                    public void onEntity(ServerLevel level, Entity entity, FluidStack fluid, float power,
                                         @Nullable LivingEntity attacker) {
                        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
                        }
                    }
                });
    }

    private static void registerPotion() {
        try {
            Class.forName("com.simibubi.create.content.fluids.potion.PotionFluid");
        } catch (ClassNotFoundException e) {
            return;
        }
        HoseSprayInteractions.register(
                f -> f instanceof com.simibubi.create.content.fluids.potion.PotionFluid,
                new HoseSprayInteraction() {
            @Override
            public void onEntity(ServerLevel level, Entity entity, FluidStack fluid, float power,
                                 @Nullable LivingEntity attacker) {
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
                    return;
                }
                net.minecraft.world.item.alchemy.PotionContents contents = fluid.getOrDefault(
                        net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        net.minecraft.world.item.alchemy.PotionContents.EMPTY);
                for (net.minecraft.world.effect.MobEffectInstance effect : contents.getAllEffects()) {
                    if (effect.getEffect().value().isInstantenous()) {
                        effect.getEffect().value().applyInstantenousEffect(null, null, living, effect.getAmplifier(),
                                1.0);
                    } else {
                        living.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
                    }
                }
            }
        });
    }
}
