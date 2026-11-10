package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import com.lycoris.loconautics.content.steelcable.SteelCableHover;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class HoseLinkWrench {

    private HoseLinkWrench() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity().isShiftKeyDown()
                || !SteelCableHover.isWrench(event.getItemStack())) {
            return;
        }
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be instanceof HoseConnectorBlockEntity || !(be instanceof SteelCableHolder holder)
                || !holder.acceptsFluidLinks()) {
            return;
        }
        ServerPlayer player = event.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (!HoseLinks.detachNewest(level, be, player)) {
            if (HoseLinks.hasAnyStrand(be)) {
                return;
            }
            level.destroyBlock(event.getPos(), !event.getEntity().isCreative());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
