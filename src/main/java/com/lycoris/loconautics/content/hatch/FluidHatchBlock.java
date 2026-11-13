package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class FluidHatchBlock extends Block implements IBE<FluidHatchBlockEntity>, IWrenchable, SimpleWaterloggedBlock {

    public static final EnumProperty<Direction> FACING =
            EnumProperty.create("facing", Direction.class, d -> true);

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public static final IntegerProperty LID_HINGE = IntegerProperty.create("lid_hinge", 0, 3);

    public static final BooleanProperty RAISED = BooleanProperty.create("raised");

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final double BARREL_BULGE = 0.5;

    private static final VoxelShape[] SHAPES = new VoxelShape[6];
    private static final VoxelShape[] SHAPES_RAISED = new VoxelShape[6];

    static {
        SHAPES[Direction.DOWN.get3DDataValue()] = Shapes.box(0.125, 0.0, 0.125, 0.875, 0.25, 0.875);
        SHAPES[Direction.UP.get3DDataValue()] = Shapes.box(0.125, 0.75, 0.125, 0.875, 1.0, 0.875);
        SHAPES[Direction.NORTH.get3DDataValue()] = Shapes.box(0.125, 0.125, 0.0, 0.875, 0.875, 0.25);
        SHAPES[Direction.SOUTH.get3DDataValue()] = Shapes.box(0.125, 0.125, 0.75, 0.875, 0.875, 1.0);
        SHAPES[Direction.WEST.get3DDataValue()] = Shapes.box(0.0, 0.125, 0.125, 0.25, 0.875, 0.875);
        SHAPES[Direction.EAST.get3DDataValue()] = Shapes.box(0.75, 0.125, 0.125, 1.0, 0.875, 0.875);
        for (Direction dir : Direction.values()) {
            Direction dome = dir.getOpposite();
            SHAPES_RAISED[dir.get3DDataValue()] = SHAPES[dir.get3DDataValue()]
                    .move(dome.getStepX() * BARREL_BULGE, dome.getStepY() * BARREL_BULGE,
                            dome.getStepZ() * BARREL_BULGE);
        }
    }

    public FluidHatchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.DOWN).setValue(OPEN, false).setValue(LID_HINGE, 0)
                .setValue(RAISED, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LID_HINGE, RAISED, WATERLOGGED);
    }

    private static boolean onBarrel(BlockGetter level, BlockPos pos, Direction face) {
        return level.getBlockState(pos.relative(face)).is(BoilerTags.BOILER_BODY);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        FluidHatchBlockEntity be = getBlockEntity(context.getLevel(), context.getClickedPos());
        if (be != null && be.isDocked()) {
            return InteractionResult.PASS;
        }
        return IWrenchable.super.onWrenched(state, context);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        Direction.Axis axis = targetedFace.getAxis();
        Direction side = hingeSide(state.getValue(FACING), state.getValue(LID_HINGE));
        Direction facing = state.getValue(FACING).getClockWise(axis);
        return state.setValue(FACING, facing).setValue(LID_HINGE, hingeFor(facing, side.getClockWise(axis)));
    }

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        BlockState state = newState.setValue(RAISED,
                onBarrel(context.getLevel(), context.getClickedPos(), newState.getValue(FACING)));
        return IWrenchable.super.updateAfterWrenched(state, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        return defaultBlockState().setValue(FACING, face)
                .setValue(RAISED, onBarrel(context.getLevel(), context.getClickedPos(), face))
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER)
                .setValue(LID_HINGE, hingeFor(face, hingeSideAimedAt(context, face)));
    }

    private static Direction hingeSideAimedAt(BlockPlaceContext context, Direction face) {
        Vec3 offset = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
        Direction best = null;
        double bestOffset = 0.15;
        for (Direction side : Direction.values()) {
            if (side.getAxis() == face.getAxis()) {
                continue;
            }
            double along = side.getAxis().choose(offset.x, offset.y, offset.z) * side.getAxisDirection().getStep();
            if (along > bestOffset) {
                best = side;
                bestOffset = along;
            }
        }
        if (best != null) {
            return best;
        }
        return face.getAxis().isVertical() ? context.getHorizontalDirection() : Direction.UP;
    }

    // the rotations mirror HatchRenderer, the lid hinge sits on the model's +z edge before they run
    public static Direction hingeSide(Direction facing, int hinge) {
        double h = Math.toRadians(hinge * 90.0 + (facing == Direction.SOUTH ? 180.0 : 0.0));
        double[] v = {Math.sin(h), 0.0, Math.cos(h)};
        switch (facing) {
            case UP -> rotateX(v, 180);
            case NORTH -> rotateX(v, 90);
            case SOUTH -> rotateX(v, 270);
            case WEST -> {
                rotateX(v, 90);
                rotateY(v, 90);
            }
            case EAST -> {
                rotateX(v, 90);
                rotateY(v, 270);
            }
            default -> { }
        }
        return Direction.getNearest(v[0], v[1], v[2]);
    }

    public static int hingeFor(Direction facing, Direction side) {
        for (int hinge = 0; hinge < 4; hinge++) {
            if (hingeSide(facing, hinge) == side) {
                return hinge;
            }
        }
        return 0;
    }

    private static void rotateX(double[] v, double degrees) {
        double a = Math.toRadians(degrees);
        double y = v[1] * Math.cos(a) - v[2] * Math.sin(a);
        double z = v[1] * Math.sin(a) + v[2] * Math.cos(a);
        v[1] = y;
        v[2] = z;
    }

    private static void rotateY(double[] v, double degrees) {
        double a = Math.toRadians(degrees);
        double x = v[0] * Math.cos(a) + v[2] * Math.sin(a);
        double z = -v[0] * Math.sin(a) + v[2] * Math.cos(a);
        v[0] = x;
        v[2] = z;
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
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int face = state.getValue(FACING).get3DDataValue();
        return state.getValue(RAISED) ? SHAPES_RAISED[face] : SHAPES[face];
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        boolean open = !state.getValue(OPEN);
        if (!open) {
            FluidHatchBlockEntity be = getBlockEntity(level, pos);
            if (be != null && be.isDocked()) {
                return InteractionResult.CONSUME;
            }
        }
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return IBE.super.getTicker(level, state, type);
    }

    @Override
    public Class<FluidHatchBlockEntity> getBlockEntityClass() {
        return FluidHatchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidHatchBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.FLUID_HATCH_BE.get();
    }
}
