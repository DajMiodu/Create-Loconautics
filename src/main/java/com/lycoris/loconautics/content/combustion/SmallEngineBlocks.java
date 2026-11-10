package com.lycoris.loconautics.content.combustion;

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

public final class SmallEngineBlocks {

    private SmallEngineBlocks() {}

    public static final String BASE_NAME = "small_combustion_engine";

    public static final DyeColor TAB_COLOR = DyeColor.RED;

    public static final Map<DyeColor, DeferredHolder<Block, SmallEngineBlock>> ENGINES = registerBlocks();

    public static final Map<DyeColor, DeferredHolder<Item, BlockItem>> ENGINE_ITEMS = registerItems();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmallEngineBlockEntity>>
            ENGINE_BE = LoconauticsRegistries.BLOCK_ENTITIES.register(BASE_NAME, () ->
            BlockEntityType.Builder.of(SmallEngineBlocks::createBE,
                    ENGINES.values().stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));

    private static SmallEngineBlockEntity createBE(BlockPos pos, BlockState state) {
        return new SmallEngineBlockEntity(ENGINE_BE.get(), pos, state);
    }

    public static String name(DyeColor color) {
        return color.getSerializedName() + "_" + BASE_NAME;
    }

    private static Map<DyeColor, DeferredHolder<Block, SmallEngineBlock>> registerBlocks() {
        Map<DyeColor, DeferredHolder<Block, SmallEngineBlock>> blocks = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            blocks.put(color, LoconauticsRegistries.BLOCKS.register(name(color), () ->
                    new SmallEngineBlock(LoconauticsBlockProperties.metallic().noOcclusion(), color)));
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static Map<DyeColor, DeferredHolder<Item, BlockItem>> registerItems() {
        Map<DyeColor, DeferredHolder<Item, BlockItem>> items = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            items.put(color, LoconauticsRegistries.ITEMS.register(name(color), () ->
                    new SmallEngineBlockItem(ENGINES.get(color).get(), new Item.Properties().stacksTo(64))));
        }
        return Collections.unmodifiableMap(items);
    }

    public static void init() {}
}
