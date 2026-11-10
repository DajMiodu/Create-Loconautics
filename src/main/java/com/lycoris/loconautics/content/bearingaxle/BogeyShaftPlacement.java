package com.lycoris.loconautics.content.bearingaxle;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.placement.PoleHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Predicate;

public final class BogeyShaftPlacement extends PoleHelper<Direction.Axis> {

    public static final int ID = PlacementHelpers.register(new BogeyShaftPlacement());

    private BogeyShaftPlacement() {
        super(state -> BogeyShaft.isShafted(state) || AllBlocks.SHAFT.has(state) || AllBlocks.POWERED_SHAFT.has(state),
                BogeyShaftPlacement::axisOf, BlockStateProperties.AXIS);
    }

    private static Direction.Axis axisOf(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
                ? state.getValue(BlockStateProperties.HORIZONTAL_AXIS)
                : state.getValue(BlockStateProperties.AXIS);
    }

    public static void init() {
    }

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AbstractSimpleShaftBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return BogeyShaft::isShafted;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult ray) {
        PlacementOffset offset = super.getOffset(player, level, state, pos, ray);
        if (!offset.isSuccessful()) {
            return offset;
        }
        Direction.Axis axis = axisOf(state);
        BlockPos target = offset.getBlockPos();
        return offset.withTransform(shaft -> {
            BlockState aligned = shaft.setValue(BlockStateProperties.AXIS, axis);
            return level.isClientSide() ? aligned : ShaftBlock.pickCorrectShaftType(aligned, level, target);
        });
    }
}
