package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class FluidHatchBlock extends Block implements IBE<FluidHatchBlockEntity>, IWrenchable {

    public static final EnumProperty<Direction> FACING =
            EnumProperty.create("facing", Direction.class, d -> true);

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public static final IntegerProperty LID_HINGE = IntegerProperty.create("lid_hinge", 0, 3);

    public static final BooleanProperty RAISED = BooleanProperty.create("raised");

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
                .setValue(RAISED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LID_HINGE, RAISED);
    }

    private static boolean onBarrel(BlockGetter level, BlockPos pos, Direction face) {
        return level.getBlockState(pos.relative(face)).is(BoilerTags.BOILER_BODY);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            int next = (state.getValue(LID_HINGE) + 1) % 4;
            level.setBlock(context.getClickedPos(), state.setValue(LID_HINGE, next), Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, face)
                .setValue(RAISED, onBarrel(context.getLevel(), context.getClickedPos(), face));
        if (face.getAxis().isVertical()) {
            state = state.setValue(LID_HINGE, context.getHorizontalDirection().get2DDataValue());
        }
        return state;
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
