package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.foundation.LoconauticsBlockProperties;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class AnalogControllerBlocks {

    private AnalogControllerBlocks() {}

    public static final DyeColor TAB_COLOR = DyeColor.RED;

    public static final Map<DyeColor, DeferredHolder<Block, AnalogControllerBlock>> CONTROLLERS =
            registerControllers();

    public static final Map<DyeColor, DeferredHolder<Item, BlockItem>> CONTROLLER_ITEMS = registerControllerItems();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnalogControllerBlockEntity>>
            CONTROLLER_BE = LoconauticsRegistries.BLOCK_ENTITIES.register("analog_controller", () ->
            BlockEntityType.Builder
                    .of(AnalogControllerBlocks::createBE, CONTROLLERS.values().stream()
                            .map(DeferredHolder::get).toArray(Block[]::new))
                    .build(null));

    public static String controllerName(DyeColor color) {
        return color.getSerializedName() + "_analog_controller";
    }

    private static Map<DyeColor, DeferredHolder<Block, AnalogControllerBlock>> registerControllers() {
        Map<DyeColor, DeferredHolder<Block, AnalogControllerBlock>> blocks = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            blocks.put(color, LoconauticsRegistries.BLOCKS.register(controllerName(color), () ->
                    new AnalogControllerBlock(LoconauticsBlockProperties.metallic(), color)));
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static Map<DyeColor, DeferredHolder<Item, BlockItem>> registerControllerItems() {
        Map<DyeColor, DeferredHolder<Item, BlockItem>> items = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            items.put(color, LoconauticsRegistries.ITEMS.register(controllerName(color), () ->
                    new BlockItem(CONTROLLERS.get(color).get(), new Item.Properties().stacksTo(64))));
        }
        return Collections.unmodifiableMap(items);
    }

    static AnalogControllerBlockEntity createBE(BlockPos pos, BlockState state) {
        return new AnalogControllerBlockEntity(CONTROLLER_BE.get(), pos, state);
    }
}
