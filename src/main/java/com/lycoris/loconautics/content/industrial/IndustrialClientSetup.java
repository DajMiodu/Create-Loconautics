package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.CreateClient;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;

public final class IndustrialClientSetup {

    private IndustrialClientSetup() {}

    public static void register() {
        for (Block block : new Block[]{IndustrialBlocks.GLASS_PIPE.get()}) {
            ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutoutMipped());
        }

        for (Block block : new Block[]{IndustrialBlocks.PIPE.get(), IndustrialBlocks.GLASS_PIPE.get()}) {
            swap(block, IndustrialPipeAttachmentModel::withAO);
        }
    }

    private static void swap(Block block,
                             java.util.function.Function<net.minecraft.client.resources.model.BakedModel,
                                     net.minecraft.client.resources.model.BakedModel> factory) {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(RegisteredObjectsHelper.getKeyOrThrow(block), factory::apply);
    }
}
