package com.lycoris.loconautics.content.relocator;

import com.lycoris.loconautics.foundation.ui.GuiCroppedItem;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class CreativeTrainRelocatorItem extends Item implements GuiCroppedItem {

    public CreativeTrainRelocatorItem(Properties properties) {
        super(properties);
    }

    @OnlyIn(Dist.CLIENT)
    public static IClientItemExtensions clientExtensions() {
        return new IClientItemExtensions() {
            private final CustomRenderedItemModelRenderer renderer = new CreativeTrainRelocatorItemRenderer();

            @Override
            public CustomRenderedItemModelRenderer getCustomRenderer() {
                return renderer;
            }
        };
    }
}