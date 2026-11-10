package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlock;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlock;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.boiler.firebox.FireboxStockerBlock;
import com.lycoris.loconautics.content.boiler.firebox.FireboxStockerBlockEntity;
import com.lycoris.loconautics.content.boiler.safetyvalve.SmartSafetyValveBlock;
import com.lycoris.loconautics.content.boiler.safetyvalve.SmartSafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlock;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlock;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockItem;
import com.lycoris.loconautics.foundation.LoconauticsBlockProperties;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BoilerBlocks {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LoconauticsConstants.MOD_ID);

    private static BlockBehaviour.Properties stoneish() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(3.5f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties barrel() {
        return LoconauticsBlockProperties.metallic().noOcclusion();
    }

    public static final DeferredHolder<Block, FireboxBlock> FIREBOX =
            LoconauticsRegistries.BLOCKS.register("firebox", () -> new FireboxBlock(
                    stoneish().sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 13 : 0)));

    public static final DeferredHolder<Item, BlockItem> FIREBOX_ITEM =
            LoconauticsRegistries.ITEMS.register("firebox", () ->
                    new BlockItem(FIREBOX.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FireboxBlockEntity>>
            FIREBOX_BE = BLOCK_ENTITIES.register("firebox", () ->
            BlockEntityType.Builder
                    .of(BoilerBlocks::createFireboxBE, FIREBOX.get())
                    .build(null));

    public static final DeferredHolder<Block, BoilerBarrelBlock> BOILER_BODY =
            LoconauticsRegistries.BLOCKS.register("boiler_body", () ->
                    new BoilerBarrelBlock(barrel(), "colorless", false));

    public static final DeferredHolder<Item, BlockItem> BOILER_BODY_ITEM =
            LoconauticsRegistries.ITEMS.register("boiler_body", () ->
                    new com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelItem(BOILER_BODY.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlockEntity>>
            BOILER_BARREL_BE = BLOCK_ENTITIES.register("boiler_barrel", () ->
            BlockEntityType.Builder
                    .of(BoilerBlocks::createBoilerBarrelBE, BOILER_BODY.get())
                    .build(null));

    static com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlockEntity createBoilerBarrelBE(
            BlockPos pos, BlockState state) {
        return new com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlockEntity(
                BOILER_BARREL_BE.get(), pos, state);
    }

    public static final DeferredHolder<Block,
            com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlock> BARREL_PIPE_CONNECTOR =
            LoconauticsRegistries.BLOCKS.register("barrel_pipe_connector", () ->
                    new com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlock(barrel()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlockEntity>>
            BARREL_PIPE_CONNECTOR_BE = BLOCK_ENTITIES.register("barrel_pipe_connector", () ->
            BlockEntityType.Builder
                    .of(BoilerBlocks::createBarrelConnectorBE, BARREL_PIPE_CONNECTOR.get())
                    .build(null));

    public static final DeferredHolder<Block, SteamTakeoffBlock> STEAM_TAKEOFF =
            LoconauticsRegistries.BLOCKS.register("steam_takeoff", () -> new SteamTakeoffBlock(LoconauticsBlockProperties.metallic()));

    public static final DeferredHolder<Item, BlockItem> STEAM_TAKEOFF_ITEM =
            LoconauticsRegistries.ITEMS.register("steam_takeoff", () ->
                    new BlockItem(STEAM_TAKEOFF.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamTakeoffBlockEntity>>
            STEAM_TAKEOFF_BE = BLOCK_ENTITIES.register("steam_takeoff", () ->
            BlockEntityType.Builder
                    .of(BoilerBlocks::createSteamTakeoffBE, STEAM_TAKEOFF.get())
                    .build(null));

    public static final DeferredHolder<Block, SmartSafetyValveBlock> SMART_SAFETY_VALVE =
            LoconauticsRegistries.BLOCKS.register("smart_safety_valve", () ->
                    new SmartSafetyValveBlock(LoconauticsBlockProperties.metallic().noOcclusion()));

    public static final DeferredHolder<Item, BlockItem> SMART_SAFETY_VALVE_ITEM =
            LoconauticsRegistries.ITEMS.register("smart_safety_valve", () ->
                    new BlockItem(SMART_SAFETY_VALVE.get(),
                            new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartSafetyValveBlockEntity>>
            SMART_SAFETY_VALVE_BE = BLOCK_ENTITIES.register("smart_safety_valve", () ->
            BlockEntityType.Builder
                    .of(BoilerBlocks::createSmartSafetyValveBE, SMART_SAFETY_VALVE.get())
                    .build(null));

    static SmartSafetyValveBlockEntity createSmartSafetyValveBE(BlockPos pos, BlockState state) {
        return new SmartSafetyValveBlockEntity(SMART_SAFETY_VALVE_BE.get(), pos, state);
    }

    public static final DeferredHolder<Block, FireboxStockerBlock> FIREBOX_STOCKER =
            LoconauticsRegistries.BLOCKS.register("firebox_stocker", () ->
                    new FireboxStockerBlock(LoconauticsBlockProperties.metallic()));

    public static final DeferredHolder<Item, BlockItem> FIREBOX_STOCKER_ITEM =
            LoconauticsRegistries.ITEMS.register("firebox_stocker", () ->
                    new BlockItem(FIREBOX_STOCKER.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FireboxStockerBlockEntity>>
            FIREBOX_STOCKER_BE = BLOCK_ENTITIES.register("firebox_stocker", () ->
            BlockEntityType.Builder
                    .of(BoilerBlocks::createFireboxStockerBE, FIREBOX_STOCKER.get())
                    .build(null));

    public static FireboxStockerBlockEntity createFireboxStockerBE(BlockPos pos, BlockState state) {
        return new FireboxStockerBlockEntity(FIREBOX_STOCKER_BE.get(), pos, state);
    }

    public static final DeferredHolder<Block, GaugeBlock>
            LOCOMOTIVE_GAUGE = LoconauticsRegistries.BLOCKS.register("locomotive_gauge", () ->
            new GaugeBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()));

    public static final DeferredHolder<Item, GaugeBlockItem>
            LOCOMOTIVE_GAUGE_ITEM = LoconauticsRegistries.ITEMS.register("locomotive_gauge", () ->
            new GaugeBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, com.lycoris.loconautics.content.controls.panelvalve.ValveBlockItem>
            VALVE_ITEM = LoconauticsRegistries.ITEMS.register("locomotive_valve", () ->
            new com.lycoris.loconautics.content.controls.panelvalve.ValveBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, com.lycoris.loconautics.content.controls.panelbutton.ButtonBlockItem>
            GAUGE_BUTTON_ITEM = LoconauticsRegistries.ITEMS.register("gauge_button", () ->
            new com.lycoris.loconautics.content.controls.panelbutton.ButtonBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, com.lycoris.loconautics.content.controls.panellever.LeverBlockItem>
            GAUGE_LEVER_ITEM = LoconauticsRegistries.ITEMS.register("gauge_lever", () ->
            new com.lycoris.loconautics.content.controls.panellever.LeverBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, com.lycoris.loconautics.content.controls.panelswitch.SwitchBlockItem>
            GAUGE_SWITCH_ITEM = LoconauticsRegistries.ITEMS.register("gauge_switch", () ->
            new com.lycoris.loconautics.content.controls.panelswitch.SwitchBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<GaugeBlockEntity>>
            LOCOMOTIVE_GAUGE_BE = BLOCK_ENTITIES.register("locomotive_gauge", () ->
            BlockEntityType.Builder
                    .of(BoilerBlocks::createLocomotiveGaugeBE, LOCOMOTIVE_GAUGE.get())
                    .build(null));

    static GaugeBlockEntity createLocomotiveGaugeBE(
            BlockPos pos, BlockState state) {
        return new GaugeBlockEntity(
                LOCOMOTIVE_GAUGE_BE.get(), pos, state);
    }

    static SteamTakeoffBlockEntity createSteamTakeoffBE(BlockPos pos, BlockState state) {
        return new SteamTakeoffBlockEntity(STEAM_TAKEOFF_BE.get(), pos, state);
    }

    public static FireboxBlockEntity createFireboxBE(BlockPos pos, BlockState state) {
        return new FireboxBlockEntity(FIREBOX_BE.get(), pos, state);
    }

    public static com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlockEntity createBarrelConnectorBE(
            BlockPos pos, BlockState state) {
        return new com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlockEntity(
                BARREL_PIPE_CONNECTOR_BE.get(), pos, state);
    }

    private BoilerBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
