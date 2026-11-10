package com.lycoris.loconautics.content.industrial;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class IndustrialBlocks {

    public static final float CAPACITY_FACTOR = 1.5f;

    public static final float RANGE_FACTOR = 1.5f;

    public static int pumpRange(BlockState pump, int baseRange) {
        return pump.is(PUMP.get()) ? (int) (baseRange * RANGE_FACTOR) : baseRange;
    }

    private static BlockBehaviour.Properties copperish() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_LIGHT_GRAY)
                .strength(3.0f)
                .sound(SoundType.COPPER)
                .requiresCorrectToolForDrops();
    }

    public static final DeferredHolder<Block, IndustrialPipeBlock> PIPE =
            LoconauticsRegistries.BLOCKS.register("industrial_pipe", () ->
                    new IndustrialPipeBlock(copperish().forceSolidOff()));

    public static final DeferredHolder<Item, BlockItem> PIPE_ITEM =
            LoconauticsRegistries.ITEMS.register("industrial_pipe", () ->
                    new BlockItem(PIPE.get(), new Item.Properties()));

    public static final DeferredHolder<Block, IndustrialGlassPipeBlock> GLASS_PIPE =
            LoconauticsRegistries.BLOCKS.register("industrial_glass_pipe", () ->
                    new IndustrialGlassPipeBlock(copperish().noOcclusion()));

    public static final DeferredHolder<Block, PumpBlock> PUMP =
            LoconauticsRegistries.BLOCKS.register("industrial_pump", () ->
                    new PumpBlock(copperish().mapColor(MapColor.STONE)));

    public static final DeferredHolder<Item, BlockItem> PUMP_ITEM =
            LoconauticsRegistries.ITEMS.register("industrial_pump", () ->
                    new BlockItem(PUMP.get(), new Item.Properties()));

    public static final DeferredHolder<Block, SmartFluidPipeBlock> SMART_PIPE =
            LoconauticsRegistries.BLOCKS.register("industrial_smart_pipe", () ->
                    new SmartFluidPipeBlock(copperish().mapColor(MapColor.TERRACOTTA_YELLOW)));

    public static final DeferredHolder<Item, BlockItem> SMART_PIPE_ITEM =
            LoconauticsRegistries.ITEMS.register("industrial_smart_pipe", () ->
                    new BlockItem(SMART_PIPE.get(), new Item.Properties()));

    public static final DeferredHolder<Block, FluidValveBlock> VALVE =
            LoconauticsRegistries.BLOCKS.register("industrial_valve", () ->
                    new FluidValveBlock(copperish()));

    public static final DeferredHolder<Item, BlockItem> VALVE_ITEM =
            LoconauticsRegistries.ITEMS.register("industrial_valve", () ->
                    new BlockItem(VALVE.get(), new Item.Properties()));

    public static final DeferredHolder<Block, IndustrialTankBlock> TANK =
            LoconauticsRegistries.BLOCKS.register("industrial_fluid_tank", () ->
                    new IndustrialTankBlock(copperish().noOcclusion()
                            .isRedstoneConductor((s, l, p) -> true)));

    public static final DeferredHolder<Item, IndustrialTankItem> TANK_ITEM =
            LoconauticsRegistries.ITEMS.register("industrial_fluid_tank", () ->
                    new IndustrialTankItem(TANK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialTankBlockEntity>> TANK_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("industrial_fluid_tank", () ->
                    BlockEntityType.Builder.of(IndustrialBlocks::createTankBE, TANK.get()).build(null));

    private IndustrialBlocks() {}

    private static IndustrialTankBlockEntity createTankBE(BlockPos pos, BlockState state) {
        return new IndustrialTankBlockEntity(TANK_BE.get(), pos, state);
    }

    public static void init() {}

    @SubscribeEvent
    public static void addToCreateBlockEntities(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.FLUID_PIPE.get(), PIPE.get());
        event.modify(AllBlockEntityTypes.GLASS_FLUID_PIPE.get(), GLASS_PIPE.get());
        event.modify(AllBlockEntityTypes.MECHANICAL_PUMP.get(), PUMP.get());
        event.modify(AllBlockEntityTypes.SMART_FLUID_PIPE.get(), SMART_PIPE.get());
        event.modify(AllBlockEntityTypes.FLUID_VALVE.get(), VALVE.get());
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TANK_BE.get(), (be, context) ->
                be.getFluidHandler());
    }
}
