package com.lycoris.loconautics.content.hose.winch;

import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class HoseWinchBlock extends DirectionalAxisKineticBlock
        implements IBE<HoseWinchBlockEntity>, BlockSubLevelAssemblyListener, BlockSubLevelCollisionShape {

    public static final net.minecraft.world.level.block.state.properties.BooleanProperty HAS_COIL =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("has_coil");

    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;

    public HoseWinchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HAS_COIL, false).setValue(POWERED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null
                : state.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_COIL, POWERED);
    }

    @Override
    protected Direction getFacingForPlacement(BlockPlaceContext context) {
        return context.getClickedFace().getOpposite();
    }

    @Override
    protected boolean getAxisAlignmentForPlacement(BlockPlaceContext context) {
        return context.getHorizontalDirection().getAxis() != Direction.Axis.X;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return switch (state.getValue(FACING).getAxis()) {
            case Y -> state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? Direction.Axis.X : Direction.Axis.Z;
            case X -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }

    private static final VoxelShape BASE_SHAPE = Shapes.or(
            Block.box(0, 0, 4, 16, 3, 12),
            Block.box(0, 2, 2, 3, 14, 14),
            Block.box(13, 2, 2, 16, 14, 14),
            Block.box(3, 4, 4, 13, 12, 12),
            Block.box(3, 13, 2, 13, 14, 14),
            Block.box(3, 14, 3, 13, 16, 13));

    private static final VoxelShape PHYSICS_BASE = Block.box(1, 0, 1, 15, 0.25, 15);

    private static final Map<Direction, VoxelShape> SHAPES_ALONG = shapes(BASE_SHAPE, true);
    private static final Map<Direction, VoxelShape> SHAPES_ACROSS = shapes(BASE_SHAPE, false);
    private static final Map<Direction, VoxelShape> PHYSICS_ALONG = shapes(PHYSICS_BASE, true);
    private static final Map<Direction, VoxelShape> PHYSICS_ACROSS = shapes(PHYSICS_BASE, false);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes =
                state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? SHAPES_ALONG : SHAPES_ACROSS;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        Map<Direction, VoxelShape> shapes =
                state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? PHYSICS_ALONG : PHYSICS_ACROSS;
        return shapes.get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> shapes(VoxelShape base, boolean along) {
        int flat = along ? 0 : 1;
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.DOWN, turn(base, 0, flat));
        shapes.put(Direction.UP, turn(base, 2, flat));
        shapes.put(Direction.NORTH, turn(base, 3, 2));
        shapes.put(Direction.SOUTH, turn(base, 3, 0));
        shapes.put(Direction.EAST, turn(base, 3, 3));
        shapes.put(Direction.WEST, turn(base, 3, 1));
        return shapes;
    }

    private static VoxelShape turn(VoxelShape shape, int quarterTurnsX, int quarterTurnsY) {
        for (int i = 0; i < quarterTurnsX; i++) {
            shape = turnClockwiseX(shape);
        }
        for (int i = 0; i < quarterTurnsY; i++) {
            shape = turnClockwiseY(shape);
        }
        return shape;
    }

    private static VoxelShape turnClockwiseX(VoxelShape shape) {
        List<VoxelShape> turned = new ArrayList<>();
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                turned.add(Shapes.box(minX, 1 - maxZ, minY, maxX, 1 - minZ, maxY)));
        return turned.stream().reduce(Shapes.empty(), Shapes::or);
    }

    private static VoxelShape turnClockwiseY(VoxelShape shape) {
        List<VoxelShape> turned = new ArrayList<>();
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                turned.add(Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
        return turned.stream().reduce(Shapes.empty(), Shapes::or);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HoseWinchBlockEntity be) {
            be.onWinchBroken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), 2);
        }
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    @Override
    public Class<HoseWinchBlockEntity> getBlockEntityClass() {
        return HoseWinchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HoseWinchBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.HOSE_WINCH_BE.get();
    }
}
