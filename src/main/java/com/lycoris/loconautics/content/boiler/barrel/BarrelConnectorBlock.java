package com.lycoris.loconautics.content.boiler.barrel;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BarrelConnectorBlock extends BaseEntityBlock implements IWrenchable, SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        double a = 3.0, b = 13.0, near = 0.0, far = 11.0, mirrorNear = 5.0, mirrorFar = 16.0;
        SHAPES[Direction.NORTH.get3DDataValue()] = box(a, a, near, b, b, far);
        SHAPES[Direction.SOUTH.get3DDataValue()] = box(a, a, mirrorNear, b, b, mirrorFar);
        SHAPES[Direction.WEST.get3DDataValue()] = box(near, a, a, far, b, b);
        SHAPES[Direction.EAST.get3DDataValue()] = box(mirrorNear, a, a, mirrorFar, b, b);
        SHAPES[Direction.DOWN.get3DDataValue()] = box(a, near, a, b, far, b);
        SHAPES[Direction.UP.get3DDataValue()] = box(a, mirrorNear, a, b, mirrorFar, b);
    }

    public BarrelConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(BarrelConnectorBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get3DDataValue()];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toPipe = state.getValue(FACING);
        Direction.Axis length = BarrelPipeConnector.bodyAxis(level.getBlockState(pos.relative(toPipe.getOpposite())));
        return length != null && length != toPipe.getAxis()
                && BarrelPipeConnector.pipeFitsFace(level.getBlockState(pos.relative(toPipe)), toPipe);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) {
            return state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BoilerBlocks.createBarrelConnectorBE(pos, state);
    }

    public static net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions clientExtensions() {
        return new net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions() {
            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos,
                                             net.minecraft.client.particle.ParticleEngine manager) {
                BlockState pipe = particlePipe(level, pos, state);
                if (pipe != null) manager.destroy(pos, pipe);
                return true;
            }
        };
    }

    @Nullable
    private static BlockState particlePipe(BlockGetter level, BlockPos pos, BlockState state) {
        Block neighbor = level.getBlockState(pos.relative(state.getValue(FACING))).getBlock();
        Block pipe = BarrelConnectorRenderer.pipeOfSameMod(neighbor);
        return pipe == null ? null : pipe.defaultBlockState();
    }

    public static void place(Level level, BlockPos pos, Direction toPipe) {
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        level.setBlock(pos, BoilerBlocks.BARREL_PIPE_CONNECTOR.get().defaultBlockState()
                .setValue(FACING, toPipe).setValue(WATERLOGGED, water), Block.UPDATE_ALL);
    }
}
