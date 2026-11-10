package com.lycoris.loconautics.content.boiler.firebox;


import com.lycoris.loconautics.common.ShiftPlacement;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class FireboxStockerBlock extends HorizontalDirectionalBlock implements IWrenchable, IBE<FireboxStockerBlockEntity> {

    public static final com.mojang.serialization.MapCodec<FireboxStockerBlock> CODEC =
            simpleCodec(FireboxStockerBlock::new);

    public FireboxStockerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, ShiftPlacement.facing(context));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !moving
                && level.getBlockEntity(pos) instanceof FireboxStockerBlockEntity be) {
            Containers.dropContents(level, pos, be.dropContents());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public Class<FireboxStockerBlockEntity> getBlockEntityClass() {
        return FireboxStockerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FireboxStockerBlockEntity> getBlockEntityType() {
        return BoilerBlocks.FIREBOX_STOCKER_BE.get();
    }
}
