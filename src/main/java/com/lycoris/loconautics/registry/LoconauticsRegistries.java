package com.lycoris.loconautics.registry;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerMenu;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleMenu;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import com.lycoris.loconautics.content.ghostwheel.GhostWheelBlock;
import com.lycoris.loconautics.content.brakes.HandbrakeBlock;
import com.lycoris.loconautics.content.headstock.CopycatHeadstockBlock;
import com.lycoris.loconautics.content.headstock.CopycatHeadstockBlockEntity;
import com.lycoris.loconautics.content.knuckle.KnuckleBlock;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.content.casingpicker.BogeyChoice;
import com.lycoris.loconautics.content.casingpicker.BogeyItem;
import com.lycoris.loconautics.content.relocator.CreativeTrainRelocatorItem;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlock;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlockEntity;
import com.lycoris.loconautics.content.turntablebearing.TurntablePlateBlock;
import com.lycoris.loconautics.content.turntablebearing.TurntablePlateBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableItem;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.content.transmission.TransmissionMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LoconauticsRegistries {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(LoconauticsConstants.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(LoconauticsConstants.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE,
                    LoconauticsConstants.MOD_ID);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU,
                    LoconauticsConstants.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                    LoconauticsConstants.MOD_ID);

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE,
                    LoconauticsConstants.MOD_ID);

    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.PARTICLE_TYPE,
                    LoconauticsConstants.MOD_ID);

    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,
            net.minecraft.core.particles.ParticleType<com.lycoris.loconautics.content.hose.spray.HoseSprayOptions>>
            HOSE_WATER_SPRAY = PARTICLE_TYPES.register("hose_water_spray",
                    () -> new net.minecraft.core.particles.ParticleType<
                            com.lycoris.loconautics.content.hose.spray.HoseSprayOptions>(false) {
                        @Override
                        public com.mojang.serialization.MapCodec<
                                com.lycoris.loconautics.content.hose.spray.HoseSprayOptions> codec() {
                            return com.lycoris.loconautics.content.hose.spray.HoseSprayOptions.codec(this);
                        }

                        @Override
                        public net.minecraft.network.codec.StreamCodec<
                                ? super net.minecraft.network.RegistryFriendlyByteBuf,
                                com.lycoris.loconautics.content.hose.spray.HoseSprayOptions> streamCodec() {
                            return com.lycoris.loconautics.content.hose.spray.HoseSprayOptions.streamCodec(this);
                        }
                    });

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BogeyChoice>>
            BOGEY_CHOICE = DATA_COMPONENTS.register("bogey_choice", () ->
                    DataComponentType.<BogeyChoice>builder()
                            .persistent(BogeyChoice.CODEC)
                            .networkSynchronized(BogeyChoice.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>>
            LOCOMOTIVE_GAUGE_TARGET = DATA_COMPONENTS.register("locomotive_gauge_target", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>>
            TURNTABLE_SIGNAL_TARGET = DATA_COMPONENTS.register("turntable_signal_target", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>>
            VALVE_TARGET = DATA_COMPONENTS.register("valve_target", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>>
            BUTTON_TARGET = DATA_COMPONENTS.register("button_target", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>>
            SWITCH_TARGET = DATA_COMPONENTS.register("switch_target", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>>
            LEVER_TARGET = DATA_COMPONENTS.register("lever_target", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>>
            STEEL_CABLE_FIRST_CONNECTION = DATA_COMPONENTS.register("steel_cable_first_connection", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<GaugeMetric>>
            LOCOMOTIVE_GAUGE_METRIC = DATA_COMPONENTS.register("locomotive_gauge_metric", () ->
                    DataComponentType.<GaugeMetric>builder()
                            .persistent(GaugeMetric.CODEC)
                            .networkSynchronized(
                                    GaugeMetric.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnalogControllerBlockEntity>>
            ANALOG_CONTROLLER_BE =
            com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlocks.CONTROLLER_BE;

    public static final DeferredHolder<MenuType<?>, MenuType<AnalogControllerMenu>>
            ANALOG_CONTROLLER_MENU = MENUS.register("analog_controller", () ->
            IMenuTypeExtension.create(LoconauticsRegistries::createAnalogControllerMenu));

    public static final DeferredHolder<Block, BearingAxleBlock> BEARING_AXLE =
            BLOCKS.register("bearing_axle", () -> new BearingAxleBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> BEARING_AXLE_ITEM =
            ITEMS.register("bearing_axle", () ->
                    new BlockItem(BEARING_AXLE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BearingAxleBlockEntity>>
            BEARING_AXLE_BE = BLOCK_ENTITIES.register("bearing_axle", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createBearingAxleBE, BEARING_AXLE.get())
                    .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<BearingAxleMenu>>
            BEARING_AXLE_MENU = MENUS.register("bearing_axle", () ->
            IMenuTypeExtension.create(LoconauticsRegistries::createBearingAxleMenu));

    public static final DeferredHolder<MenuType<?>, MenuType<PanelFrequencyMenu>>
            PANEL_FREQUENCY_MENU = MENUS.register("panel_frequency", () ->
            IMenuTypeExtension.create(LoconauticsRegistries::createPanelFrequencyMenu));

    public static final DeferredHolder<Block, TurntableBearingBlock> TURNTABLE_BEARING =
            BLOCKS.register("turntable_bearing", () -> new TurntableBearingBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> TURNTABLE_BEARING_ITEM =
            ITEMS.register("turntable_bearing", () ->
                    new BlockItem(TURNTABLE_BEARING.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurntableBearingBlockEntity>>
            TURNTABLE_BEARING_BE = BLOCK_ENTITIES.register("turntable_bearing", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createTurntableBearingBE, TURNTABLE_BEARING.get())
                    .build(null));

    public static final DeferredHolder<Block, TurntablePlateBlock> TURNTABLE_PLATE =
            BLOCKS.register("turntable_plate", () -> new TurntablePlateBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurntablePlateBlockEntity>>
            TURNTABLE_PLATE_BE = BLOCK_ENTITIES.register("turntable_plate", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createTurntablePlateBE, TURNTABLE_PLATE.get())
                    .build(null));

    public static final DeferredHolder<Block, KnuckleBlock> KNUCKLE =
            BLOCKS.register("knuckle", () -> new KnuckleBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
            ));

    public static final DeferredHolder<Item, BlockItem> KNUCKLE_ITEM =
            ITEMS.register("knuckle", () ->
                    new BlockItem(KNUCKLE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KnuckleBlockEntity>>
            KNUCKLE_BE = BLOCK_ENTITIES.register("knuckle", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createKnuckleBE, KNUCKLE.get())
                    .build(null));

    public static final DeferredHolder<Block, CopycatHeadstockBlock> COPYCAT_HEADSTOCK =
            BLOCKS.register("copycat_headstock", () -> new CopycatHeadstockBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
            ));

    public static final DeferredHolder<Item, BlockItem> COPYCAT_HEADSTOCK_ITEM =
            ITEMS.register("copycat_headstock", () ->
                    new BlockItem(COPYCAT_HEADSTOCK.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopycatHeadstockBlockEntity>>
            COPYCAT_HEADSTOCK_BE = BLOCK_ENTITIES.register("copycat_headstock", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createCopycatHeadstockBE, COPYCAT_HEADSTOCK.get())
                    .build(null));

    public static final DeferredHolder<Block, HandbrakeBlock> HANDBRAKE =
            BLOCKS.register("handbrake", () -> new HandbrakeBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> HANDBRAKE_ITEM =
            ITEMS.register("handbrake", () ->
                    new BlockItem(HANDBRAKE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Block, GhostWheelBlock> GHOST_WHEEL =
            BLOCKS.register("ghost_wheel", () -> new GhostWheelBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .noOcclusion()
                            .noLootTable()
                            .sound(SoundType.EMPTY)
                            .strength(-1.0F, 3600000.0F)
            ));

    public static final DeferredHolder<Block, TransmissionBlock> TRANSMISSION =
            BLOCKS.register("transmission", () -> new TransmissionBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> TRANSMISSION_ITEM =
            ITEMS.register("transmission", () ->
                    new BlockItem(TRANSMISSION.get(),
                            new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransmissionBlockEntity>>
            TRANSMISSION_BE = BLOCK_ENTITIES.register("transmission", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createTransmissionBE, TRANSMISSION.get())
                    .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<TransmissionMenu>>
            TRANSMISSION_MENU = MENUS.register("transmission", () ->
            IMenuTypeExtension.create(LoconauticsRegistries::createTransmissionMenu));

    public static final DeferredHolder<Item, SteelCableItem> STEEL_CABLE =
            ITEMS.register("steel_cable", () -> new SteelCableItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, Item> INDUSTRIAL_IRON_ROD =
            ITEMS.register("industrial_iron_rod", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> INDUSTRIAL_IRON_SHEET =
            ITEMS.register("industrial_iron_sheet", () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, Item> UNPROCESSED_REINFORCED_IRON_SHEET =
            ITEMS.register("unprocessed_reinforced_iron_sheet", () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, Item> BOGEY =
            ITEMS.register("bogey", () -> new BogeyItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> CREATIVE_TRAIN_RELOCATOR =
            ITEMS.register("creative_train_relocator", () -> new CreativeTrainRelocatorItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Block, com.lycoris.loconautics.content.hose.winch.HoseWinchBlock> HOSE_WINCH =
            BLOCKS.register("hose_winch", () -> new com.lycoris.loconautics.content.hose.winch.HoseWinchBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> HOSE_WINCH_ITEM =
            ITEMS.register("hose_winch", () ->
                    new BlockItem(HOSE_WINCH.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity>>
            HOSE_WINCH_BE = BLOCK_ENTITIES.register("hose_winch", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createHoseWinchBE, HOSE_WINCH.get())
                    .build(null));

    public static final DeferredHolder<Block, com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlock> HOSE_NOZZLE =
            BLOCKS.register("hose_nozzle", () -> new com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> HOSE_NOZZLE_ITEM =
            ITEMS.register("hose_nozzle", () ->
                    new BlockItem(HOSE_NOZZLE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity>>
            HOSE_NOZZLE_BE = BLOCK_ENTITIES.register("hose_nozzle", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createHoseNozzleBE, HOSE_NOZZLE.get())
                    .build(null));

    public static final DeferredHolder<Block, com.lycoris.loconautics.content.ball.BallBlock> BALL =
            BLOCKS.register("ball", () -> new com.lycoris.loconautics.content.ball.BallBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(0.5f)
                            .sound(SoundType.WOOL)
            ));

    public static final DeferredHolder<Item, BlockItem> BALL_ITEM =
            ITEMS.register("ball", () -> new BlockItem(BALL.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.lycoris.loconautics.content.ball.BallBlockEntity>>
            BALL_BE = BLOCK_ENTITIES.register("ball", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createBallBE, BALL.get())
                    .build(null));

    public static final DeferredHolder<Block, com.lycoris.loconautics.content.hose.link.HoseConnectorBlock> HOSE_CONNECTOR =
            BLOCKS.register("hose_connector", () -> new com.lycoris.loconautics.content.hose.link.HoseConnectorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
            ));

    public static final DeferredHolder<Item, BlockItem> HOSE_CONNECTOR_ITEM =
            ITEMS.register("hose_connector", () ->
                    new BlockItem(HOSE_CONNECTOR.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.lycoris.loconautics.content.hose.link.HoseConnectorBlockEntity>>
            HOSE_CONNECTOR_BE = BLOCK_ENTITIES.register("hose_connector", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createHoseConnectorBE, HOSE_CONNECTOR.get())
                    .build(null));

    public static final DeferredHolder<Block, com.lycoris.loconautics.content.hatch.FluidHatchBlock> FLUID_HATCH =
            BLOCKS.register("fluid_hatch_gray", () -> new com.lycoris.loconautics.content.hatch.FluidHatchBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> FLUID_HATCH_ITEM =
            ITEMS.register("fluid_hatch_gray", () ->
                    new BlockItem(FLUID_HATCH.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<com.lycoris.loconautics.content.hatch.FluidHatchBlockEntity>>
            FLUID_HATCH_BE = BLOCK_ENTITIES.register("fluid_hatch", () ->
            BlockEntityType.Builder
                    .of(LoconauticsRegistries::createFluidHatchBE, FLUID_HATCH.get())
                    .build(null));

    public static final DeferredHolder<Item, com.lycoris.loconautics.content.hose.HoseItem> HOSE =
            ITEMS.register("hose", () ->
                    new com.lycoris.loconautics.content.hose.HoseItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab>
            LOCONAUTICS_TAB = CREATIVE_TABS.register("main", () ->
            net.minecraft.world.item.CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.loconautics.main"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(
                            com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlocks
                                    .CONTROLLER_ITEMS.get(com.lycoris.loconautics.content.analogcontroller
                                            .AnalogControllerBlocks.TAB_COLOR).get()))
                    .displayItems((params, output) -> {
                        com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlocks.CONTROLLER_ITEMS
                                .forEach((color, item) -> output.accept(new net.minecraft.world.item.ItemStack(item.get()),
                                        color == com.lycoris.loconautics.content.analogcontroller
                                                .AnalogControllerBlocks.TAB_COLOR
                                                ? net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                                                : net.minecraft.world.item.CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));

                        output.accept(BEARING_AXLE_ITEM.get());
                        output.accept(TRANSMISSION_ITEM.get());

                        output.accept(com.lycoris.loconautics.content.controls.ControlsBlocks.LOCOMOTIVE_GAUGE_ITEM.get());
                        output.accept(com.lycoris.loconautics.content.controls.ControlsBlocks.VALVE_ITEM.get());
                        output.accept(com.lycoris.loconautics.content.controls.ControlsBlocks.GAUGE_BUTTON_ITEM.get());
                        output.accept(com.lycoris.loconautics.content.controls.ControlsBlocks.GAUGE_LEVER_ITEM.get());
                        output.accept(com.lycoris.loconautics.content.controls.ControlsBlocks.GAUGE_SWITCH_ITEM.get());

                        com.lycoris.loconautics.content.dashboard.DashboardBlocks.DASHBOARD_ITEMS
                                .forEach((color, item) -> output.accept(new net.minecraft.world.item.ItemStack(item.get()),
                                        color == com.lycoris.loconautics.content.dashboard.DashboardBlocks.TAB_COLOR
                                                ? net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                                                : net.minecraft.world.item.CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));

                        output.accept(com.simibubi.create.AllBlocks.TRACK_STATION.asStack());
                        output.accept(com.simibubi.create.AllBlocks.TRACK_SIGNAL.asStack());
                        output.accept(com.simibubi.create.AllBlocks.TRACK_OBSERVER.asStack());
                        output.accept(com.simibubi.create.AllBlocks.PACKAGE_POSTBOXES
                                .get(net.minecraft.world.item.DyeColor.RED).asStack());
                        output.accept(com.simibubi.create.AllBlocks.TRACK.asStack());
                        output.accept(com.simibubi.create.AllBlocks.RAILWAY_CASING.asStack());
                        output.accept(com.simibubi.create.AllItems.SCHEDULE.asStack());

                        output.accept(HANDBRAKE_ITEM.get());
                        output.accept(TURNTABLE_BEARING_ITEM.get());

                        output.accept(dev.simulated_team.simulated.index.SimBlocks.ROPE_CONNECTOR);
                        output.accept(STEEL_CABLE.get());
                        output.accept(COPYCAT_HEADSTOCK_ITEM.get());
                        output.accept(KNUCKLE_ITEM.get());
                        com.lycoris.loconautics.content.buffer.BufferBlocks.BUFFER_ITEMS
                                .forEach((color, item) -> output.accept(new net.minecraft.world.item.ItemStack(item.get()),
                                        color == com.lycoris.loconautics.content.buffer.BufferBlocks.TAB_COLOR
                                                ? net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                                                : net.minecraft.world.item.CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));

                        output.accept(HOSE_WINCH_ITEM.get());
                        output.accept(HOSE_NOZZLE_ITEM.get());
                        output.accept(HOSE_CONNECTOR_ITEM.get());
                        output.accept(HOSE.get());
                        output.accept(FLUID_HATCH_ITEM.get());

                        com.lycoris.loconautics.content.conductorshat.ConductorsHats.HATS
                                .forEach((color, item) -> output.accept(new net.minecraft.world.item.ItemStack(item.get()),
                                        color == com.lycoris.loconautics.content.conductorshat.ConductorsHats.TAB_COLOR
                                                ? net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                                                : net.minecraft.world.item.CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));

                        output.accept(INDUSTRIAL_IRON_SHEET.get());
                        output.accept(INDUSTRIAL_IRON_ROD.get());
                        output.accept(com.lycoris.loconautics.content.industrial.IndustrialBlocks.PIPE_ITEM.get());

                        output.accept(CREATIVE_TRAIN_RELOCATOR.get());
                    })
                    .build());

    static AnalogControllerMenu createAnalogControllerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new AnalogControllerMenu(ANALOG_CONTROLLER_MENU.get(), id, inv, buf);
    }

    static BearingAxleBlockEntity createBearingAxleBE(BlockPos pos, BlockState state) {
        return new BearingAxleBlockEntity(BEARING_AXLE_BE.get(), pos, state);
    }

    static BearingAxleMenu createBearingAxleMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new BearingAxleMenu(BEARING_AXLE_MENU.get(), id, inv, buf);
    }

    static PanelFrequencyMenu createPanelFrequencyMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new PanelFrequencyMenu(PANEL_FREQUENCY_MENU.get(), id, inv, buf);
    }

    static TurntableBearingBlockEntity createTurntableBearingBE(BlockPos pos, BlockState state) {
        return new TurntableBearingBlockEntity(TURNTABLE_BEARING_BE.get(), pos, state);
    }

    static TurntablePlateBlockEntity createTurntablePlateBE(BlockPos pos, BlockState state) {
        return new TurntablePlateBlockEntity(TURNTABLE_PLATE_BE.get(), pos, state);
    }

    static KnuckleBlockEntity createKnuckleBE(BlockPos pos, BlockState state) {
        return new KnuckleBlockEntity(KNUCKLE_BE.get(), pos, state);
    }

    private static CopycatHeadstockBlockEntity createCopycatHeadstockBE(BlockPos pos, BlockState state) {
        return new CopycatHeadstockBlockEntity(COPYCAT_HEADSTOCK_BE.get(), pos, state);
    }

    static TransmissionBlockEntity createTransmissionBE(BlockPos pos, BlockState state) {
        return new TransmissionBlockEntity(TRANSMISSION_BE.get(), pos, state);
    }

    static TransmissionMenu createTransmissionMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new TransmissionMenu(TRANSMISSION_MENU.get(), id, inv, buf);
    }

    static com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity createHoseWinchBE(BlockPos pos, BlockState state) {
        return new com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity(HOSE_WINCH_BE.get(), pos, state);
    }

    static com.lycoris.loconautics.content.hose.link.HoseConnectorBlockEntity createHoseConnectorBE(BlockPos pos, BlockState state) {
        return new com.lycoris.loconautics.content.hose.link.HoseConnectorBlockEntity(HOSE_CONNECTOR_BE.get(), pos, state);
    }

    static com.lycoris.loconautics.content.ball.BallBlockEntity createBallBE(BlockPos pos, BlockState state) {
        return new com.lycoris.loconautics.content.ball.BallBlockEntity(BALL_BE.get(), pos, state);
    }

    static com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity createHoseNozzleBE(BlockPos pos, BlockState state) {
        return new com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity(HOSE_NOZZLE_BE.get(), pos, state);
    }

    static com.lycoris.loconautics.content.hatch.FluidHatchBlockEntity createFluidHatchBE(BlockPos pos, BlockState state) {
        return new com.lycoris.loconautics.content.hatch.FluidHatchBlockEntity(FLUID_HATCH_BE.get(), pos, state);
    }

    private LoconauticsRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
    }
}