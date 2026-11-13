package com.lycoris.loconautics.content.hose.nozzle;

import com.lycoris.loconautics.common.strand.StrandRemap;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;



public class HoseNozzleBlock extends Block
        implements IWrenchable, IBE<HoseNozzleBlockEntity>, dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape,
        BlockSubLevelAssemblyListener, SimpleWaterloggedBlock {

    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(0.28125, 0.0, 0.28125, 0.71875, 0.375, 0.71875),
            Shapes.box(0.34375, 0.375, 0.34375, 0.65625, 0.5, 0.65625));

    private static final VoxelShape PHYSICS_SHAPE = Shapes.box(0.28125, 0.0, 0.28125, 0.71875, 0.3125, 0.71875);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public HoseNozzleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return PHYSICS_SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        com.lycoris.loconautics.common.SubLevelAssemblyUtil.assembleSingleBlock(serverLevel, pos, "[hose]");
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    @Override
    public Class<HoseNozzleBlockEntity> getBlockEntityClass() {
        return HoseNozzleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HoseNozzleBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.HOSE_NOZZLE_BE.get();
    }
}
