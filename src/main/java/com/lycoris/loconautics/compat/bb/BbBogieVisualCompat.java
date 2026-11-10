package com.lycoris.loconautics.compat.bb;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyBlockEntityVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BbBogieVisualCompat {

    private static final ResourceLocation BOGEY_BE_ID = ResourceLocation.fromNamespaceAndPath("create_bb", "bogey");

    private BbBogieVisualCompat() {
    }


    @SuppressWarnings("unchecked")
    public static void register() {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(BOGEY_BE_ID);
        if (type == null) {
            LoconauticsConstants.LOGGER.warn("create_bb loaded but block entity {} not found; bogeys will not animate on Sable trains", BOGEY_BE_ID);
            return;
        }
        SimpleBlockEntityVisualizer.builder((BlockEntityType<AbstractBogeyBlockEntity>) type)
                .factory(BogeyBlockEntityVisual::new)
                .skipVanillaRender(be -> true)
                .apply();
    }
}
