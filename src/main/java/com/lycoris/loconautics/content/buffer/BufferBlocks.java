package com.lycoris.loconautics.content.buffer;

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

public final class BufferBlocks {

    private BufferBlocks() {}

    public static final DyeColor TAB_COLOR = DyeColor.RED;

    public static final Map<DyeColor, DeferredHolder<Block, BufferBlock>> BUFFERS = registerBuffers();

    public static final Map<DyeColor, DeferredHolder<Item, BlockItem>> BUFFER_ITEMS = registerItems();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BufferBlockEntity>>
            BUFFER_BE = LoconauticsRegistries.BLOCK_ENTITIES.register("buffer", () ->
            BlockEntityType.Builder
                    .of(BufferBlocks::createBE, BUFFERS.values().stream()
                            .map(DeferredHolder::get).toArray(Block[]::new))
                    .build(null));

    public static String bufferName(DyeColor color) {
        return color.getSerializedName() + "_buffer";
    }

    private static Map<DyeColor, DeferredHolder<Block, BufferBlock>> registerBuffers() {
        Map<DyeColor, DeferredHolder<Block, BufferBlock>> buffers = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            buffers.put(color, LoconauticsRegistries.BLOCKS.register(bufferName(color), () ->
                    new BufferBlock(LoconauticsBlockProperties.metallic().noOcclusion(), color)));
        }
        return Collections.unmodifiableMap(buffers);
    }

    private static Map<DyeColor, DeferredHolder<Item, BlockItem>> registerItems() {
        Map<DyeColor, DeferredHolder<Item, BlockItem>> items = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            items.put(color, LoconauticsRegistries.ITEMS.register(bufferName(color), () ->
                    new BlockItem(BUFFERS.get(color).get(), new Item.Properties().stacksTo(64))));
        }
        return Collections.unmodifiableMap(items);
    }

    static BufferBlockEntity createBE(BlockPos pos, BlockState state) {
        return new BufferBlockEntity(BUFFER_BE.get(), pos, state);
    }

    public static void init() {}
}
