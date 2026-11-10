package com.lycoris.loconautics.content.brakes.pull;

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

public final class PullBrakeBlocks {

    private PullBrakeBlocks() {}

    public static final String BASE_NAME = "hand_brake";

    public static final DyeColor TAB_COLOR = DyeColor.RED;

    public static final Map<DyeColor, DeferredHolder<Block, PullBrakeBlock>> BRAKES = registerBrakes();

    public static final Map<DyeColor, DeferredHolder<Item, BlockItem>> BRAKE_ITEMS = registerBrakeItems();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PullBrakeBlockEntity>>
            PULL_BRAKE_BE = LoconauticsRegistries.BLOCK_ENTITIES.register(BASE_NAME, () ->
            BlockEntityType.Builder.of(PullBrakeBlocks::createBE, allBlocks()).build(null));

    public static String name(DyeColor color) {
        return color.getSerializedName() + "_" + BASE_NAME;
    }

    private static DeferredHolder<Block, PullBrakeBlock> registerBlock(String name, DyeColor color) {
        return LoconauticsRegistries.BLOCKS.register(name, () ->
                new PullBrakeBlock(LoconauticsBlockProperties.metallic().noOcclusion(), color));
    }

    private static DeferredHolder<Item, BlockItem> registerItem(String name,
                                                                DeferredHolder<Block, PullBrakeBlock> block) {
        return LoconauticsRegistries.ITEMS.register(name, () ->
                new BlockItem(block.get(), new Item.Properties().stacksTo(64)));
    }

    private static Map<DyeColor, DeferredHolder<Block, PullBrakeBlock>> registerBrakes() {
        Map<DyeColor, DeferredHolder<Block, PullBrakeBlock>> blocks = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            blocks.put(color, registerBlock(name(color), color));
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static Map<DyeColor, DeferredHolder<Item, BlockItem>> registerBrakeItems() {
        Map<DyeColor, DeferredHolder<Item, BlockItem>> items = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            items.put(color, registerItem(name(color), BRAKES.get(color)));
        }
        return Collections.unmodifiableMap(items);
    }

    private static Block[] allBlocks() {
        return BRAKES.values().stream()
                .map(DeferredHolder::get).toArray(Block[]::new);
    }

    private static PullBrakeBlockEntity createBE(BlockPos pos, BlockState state) {
        return new PullBrakeBlockEntity(PULL_BRAKE_BE.get(), pos, state);
    }
}
