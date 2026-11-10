package com.lycoris.loconautics.content.industrial;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class IndustrialBlocks {

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

    private IndustrialBlocks() {}

    public static void init() {}

    @SubscribeEvent
    public static void addToCreateBlockEntities(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.FLUID_PIPE.get(), PIPE.get());
        event.modify(AllBlockEntityTypes.GLASS_FLUID_PIPE.get(), GLASS_PIPE.get());
    }
}
