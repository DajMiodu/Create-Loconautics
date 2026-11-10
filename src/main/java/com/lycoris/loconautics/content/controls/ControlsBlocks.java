package com.lycoris.loconautics.content.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonBlockItem;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlock;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockItem;
import com.lycoris.loconautics.content.controls.panellever.LeverBlockItem;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchBlockItem;
import com.lycoris.loconautics.content.controls.panelvalve.ValveBlockItem;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ControlsBlocks {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LoconauticsConstants.MOD_ID);

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

    public static final DeferredHolder<Item, ValveBlockItem>
            VALVE_ITEM = LoconauticsRegistries.ITEMS.register("locomotive_valve", () ->
            new ValveBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, ButtonBlockItem>
            GAUGE_BUTTON_ITEM = LoconauticsRegistries.ITEMS.register("gauge_button", () ->
            new ButtonBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, LeverBlockItem>
            GAUGE_LEVER_ITEM = LoconauticsRegistries.ITEMS.register("gauge_lever", () ->
            new LeverBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, SwitchBlockItem>
            GAUGE_SWITCH_ITEM = LoconauticsRegistries.ITEMS.register("gauge_switch", () ->
            new SwitchBlockItem(
                    LOCOMOTIVE_GAUGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<GaugeBlockEntity>>
            LOCOMOTIVE_GAUGE_BE = BLOCK_ENTITIES.register("locomotive_gauge", () ->
            BlockEntityType.Builder
                    .of(ControlsBlocks::createLocomotiveGaugeBE, LOCOMOTIVE_GAUGE.get())
                    .build(null));

    static GaugeBlockEntity createLocomotiveGaugeBE(
            BlockPos pos, BlockState state) {
        return new GaugeBlockEntity(
                LOCOMOTIVE_GAUGE_BE.get(), pos, state);
    }

    private ControlsBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
