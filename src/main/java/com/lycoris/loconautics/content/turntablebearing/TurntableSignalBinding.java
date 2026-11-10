package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.trains.signal.SignalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class TurntableSignalBinding {

    private static final Map<UUID, BlockPos> PENDING = new ConcurrentHashMap<>();

    private TurntableSignalBinding() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player == null || !isSignal(stack)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof TurntableBearingBlockEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
            if (!level.isClientSide()) {
                toggle(player, stack, pos);
            }
            return;
        }
        if (level.isClientSide()) {
            return;
        }
        BlockPos bearing = stack.get(LoconauticsRegistries.TURNTABLE_SIGNAL_TARGET.get());
        if (bearing == null) {
            PENDING.remove(player.getUUID());
        } else {
            PENDING.put(player.getUUID(), bearing);
        }
    }

    private static void toggle(Player player, ItemStack stack, BlockPos bearing) {
        if (bearing.equals(stack.get(LoconauticsRegistries.TURNTABLE_SIGNAL_TARGET.get()))) {
            stack.remove(LoconauticsRegistries.TURNTABLE_SIGNAL_TARGET.get());
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            player.displayClientMessage(Component.translatable("loconautics.turntable_signal.unbound"), true);
            return;
        }
        stack.set(LoconauticsRegistries.TURNTABLE_SIGNAL_TARGET.get(), bearing.immutable());
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        player.displayClientMessage(Component.translatable("loconautics.turntable_signal.bound"), true);
    }

    @SubscribeEvent
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()
                || !(event.getEntity() instanceof Player player)
                || !(event.getPlacedBlock().getBlock() instanceof SignalBlock)) {
            return;
        }
        BlockPos bearing = PENDING.remove(player.getUUID());
        if (bearing != null && level.getBlockEntity(bearing) instanceof TurntableBearingBlockEntity be
                && be.bindSignal(event.getPos())) {
            player.displayClientMessage(Component.translatable("loconautics.turntable_signal.placed"), true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private static boolean isSignal(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof SignalBlock;
    }
}
