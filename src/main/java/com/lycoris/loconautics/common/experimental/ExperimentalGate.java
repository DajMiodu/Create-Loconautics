package com.lycoris.loconautics.common.experimental;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class ExperimentalGate {

    private ExperimentalGate() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (ExperimentalFeature.disabled(event.getItemStack().getItem())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (ExperimentalFeature.disabled(event.getItemStack().getItem())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (ExperimentalFeature.disabled(event.getPlacedBlock().getBlock().asItem())) {
            event.setCanceled(true);
        }
    }
}
