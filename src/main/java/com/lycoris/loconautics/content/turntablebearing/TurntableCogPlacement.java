package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Predicate;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class TurntableCogPlacement {

    private static IPlacementHelper smallHelper;
    private static IPlacementHelper largeHelper;

    private TurntableCogPlacement() {
    }

    public static void register() {
        smallHelper = new TurntableSmallCogHelper();
        largeHelper = new TurntableLargeCogHelper();
        PlacementHelpers.register(smallHelper);
        PlacementHelpers.register(largeHelper);
    }

    private static boolean isTurntable(BlockState state) {
        return state.getBlock() instanceof TurntableBearingBlock;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (smallHelper == null || player.isShiftKeyDown() || !(stack.getItem() instanceof CogwheelBlockItem item)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!isTurntable(state) || !ICogWheel.isDedicatedCogItem(stack)) {
            return;
        }
        IPlacementHelper helper = ICogWheel.isLargeCogItem(stack) ? largeHelper : smallHelper;
        BlockHitResult ray = event.getHitVec();
        PlacementOffset offset = helper.getOffset(player, level, state, pos, ray);
        if (!offset.isSuccessful()) {
            return;
        }
        event.setCancellationResult(offset.placeInWorld(level, item, player, event.getHand(), ray).result());
        event.setCanceled(true);
    }

    private static final class TurntableSmallCogHelper extends CogwheelBlockItem.IntegratedSmallCogHelper {
        @Override
        public Predicate<BlockState> getStatePredicate() {
            return TurntableCogPlacement::isTurntable;
        }
    }

    private static final class TurntableLargeCogHelper extends CogwheelBlockItem.IntegratedLargeCogHelper {
        @Override
        public Predicate<BlockState> getStatePredicate() {
            return TurntableCogPlacement::isTurntable;
        }
    }
}
