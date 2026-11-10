package com.lycoris.loconautics.content.cerisene;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CeriseneBlocks {

    private CeriseneBlocks() {}

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, LoconauticsConstants.MOD_ID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, LoconauticsConstants.MOD_ID);

    public static final DeferredHolder<Item, Item> RAW_CERISENE =
            LoconauticsRegistries.ITEMS.register("raw_cerisene", () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .food(new FoodProperties.Builder()
                            .nutrition(2)
                            .saturationModifier(0.2f)
                            .alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200), 1.0f)
                            .effect(() -> new MobEffectInstance(MobEffects.POISON, 100), 1.0f)
                            .effect(() -> new MobEffectInstance(MobEffects.GLOWING, 600), 1.0f)
                            .build())));

    public static final DeferredHolder<Block, CeriseneBlock> CERISENE_BLOCK =
            LoconauticsRegistries.BLOCKS.register("cerisene_block", () -> new CeriseneBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PINK)
                            .strength(0.8f)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> CeriseneTuning.LIGHT_LEVEL)
                            .noOcclusion()
                            .isValidSpawn((state, level, pos, type) -> false)
                            .isRedstoneConductor((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)));

    public static final DeferredHolder<Item, BlockItem> CERISENE_BLOCK_ITEM =
            LoconauticsRegistries.ITEMS.register("cerisene_block", () ->
                    new BlockItem(CERISENE_BLOCK.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<FluidType, FluidType> CERISENE_TYPE =
            FLUID_TYPES.register("cerisene", () -> new CeriseneFluidType(FluidType.Properties.create()
                    .density(1200)
                    .viscosity(1500)
                    .temperature(300)
                    .lightLevel(CeriseneTuning.LIGHT_LEVEL)
                    .canSwim(false)
                    .canDrown(false)
                    .supportsBoating(false)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CERISENE =
            FLUIDS.register("cerisene", () -> new BaseFlowingFluid.Source(fluidProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> CERISENE_FLOWING =
            FLUIDS.register("flowing_cerisene", () -> new BaseFlowingFluid.Flowing(fluidProperties()));

    public static final DeferredHolder<Block, LiquidBlock> CERISENE_FLUID_BLOCK =
            LoconauticsRegistries.BLOCKS.register("cerisene_fluid", () -> new LiquidBlock(CERISENE.get(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PINK)
                            .replaceable()
                            .noCollission()
                            .strength(100.0f)
                            .pushReaction(PushReaction.DESTROY)
                            .liquid()
                            .noLootTable()
                            .lightLevel(state -> CeriseneTuning.LIGHT_LEVEL)));

    public static final DeferredHolder<Item, BucketItem> CERISENE_BUCKET =
            LoconauticsRegistries.ITEMS.register("cerisene_bucket", () -> new BucketItem(CERISENE.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private static BaseFlowingFluid.Properties fluidProperties() {
        return new BaseFlowingFluid.Properties(CERISENE_TYPE, CERISENE, CERISENE_FLOWING)
                .bucket(CERISENE_BUCKET)
                .block(CERISENE_FLUID_BLOCK);
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }
}
