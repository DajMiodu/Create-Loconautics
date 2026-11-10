package com.lycoris.loconautics.content.combustion;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Predicate;

public class SmallEngineBlockItem extends BlockItem {

    private static final int HELPER_ID = PlacementHelpers.register(new EngineRowHelper());

    public SmallEngineBlockItem(SmallEngineBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        IPlacementHelper helper = PlacementHelpers.get(HELPER_ID);
        if (helper.matchesState(state) && player != null && !player.isShiftKeyDown()) {
            BlockHitResult ray = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, true);
            return helper.getOffset(player, level, state, pos, ray)
                    .placeInWorld(level, this, player, context.getHand(), ray).result();
        }
        return super.onItemUseFirst(stack, context);
    }

    private static class EngineRowHelper implements IPlacementHelper {

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof SmallEngineBlockItem;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof SmallEngineBlock;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                         BlockHitResult ray) {
            Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
            if (EngineRow.lengthAround(level, pos, facing) >= SmallEngineTuning.MAX_TANDEM) {
                return PlacementOffset.fail();
            }

            BlockPos front = EngineRow.headOf(level, pos, facing).relative(facing);
            BlockPos rear = EngineRow.tailOf(level, pos, facing).relative(facing.getOpposite());
            Direction view = player.getDirection();
            Direction preferred = view.getAxis() == facing.getAxis() ? view : view.getClockWise();
            boolean frontNearer = preferred == facing;

            BlockPos first = frontNearer ? front : rear;
            BlockPos second = frontNearer ? rear : front;
            for (BlockPos target : new BlockPos[]{first, second}) {
                if (!level.getBlockState(target).canBeReplaced()) continue;
                return PlacementOffset.success(target,
                        placed -> placed.setValue(HorizontalKineticBlock.HORIZONTAL_FACING, facing)
                                .setValue(SmallEngineBlock.PART, SmallEngineBlock.placedPart(level, target, facing)));
            }
            return PlacementOffset.fail();
        }
    }
}
