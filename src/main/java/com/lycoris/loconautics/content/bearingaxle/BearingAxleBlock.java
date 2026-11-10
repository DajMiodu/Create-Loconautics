package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BearingAxleBlock extends HorizontalKineticBlock implements IBE<BearingAxleBlockEntity> {

    public BearingAxleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    public static Direction front(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    public static Direction shaftFace(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    public static Direction getPlusFace(BlockState state) {
        return front(state).getClockWise();
    }

    public static int gearSignal(Level level, BlockPos pos, BlockState state) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).getBlock() instanceof AnalogControllerBlock) {
            return 0;
        }
        return level.getSignal(above, Direction.UP);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof BearingAxleBlockEntity be)) {
            return;
        }
        be.setPlacedTime(level.getGameTime());
        be.setGearSignal(gearSignal(level, pos, state));
        if (!com.lycoris.loconautics.content.frequency.FrequencyBindUtil.isBound(stack)) {
            return;
        }
        var reg = level.registryAccess();
        net.minecraft.world.item.ItemStack[] left =
                com.lycoris.loconautics.content.frequency.FrequencyBindUtil.read(stack, reg, "Left");
        if (left != null) be.setLeftFrequency(left[0], left[1]);
        net.minecraft.world.item.ItemStack[] right =
                com.lycoris.loconautics.content.frequency.FrequencyBindUtil.read(stack, reg, "Right");
        if (right != null) be.setRightFrequency(right[0], right[1]);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide || newState.is(this)) {
            return;
        }
        BlockPos bogey = BearingAxleValidity.driverBogey(level, pos);
        if (bogey == null) {
            return;
        }
        BlockState bogeyState = level.getBlockState(bogey);
        level.setBlocksDirty(bogey, bogeyState, bogeyState);
    }

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(1, 1, 1, 15, 15, 16),
            Block.box(6, 0, 1, 10, 16, 16),
            Block.box(0, 6, 1, 16, 10, 16),
            Block.box(0, 2, 8, 16, 14, 12));

    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0, 1, 1, 15, 15, 15),
            Block.box(0, 0, 6, 15, 16, 10),
            Block.box(0, 6, 0, 15, 10, 16),
            Block.box(4, 2, 0, 8, 14, 16));

    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(1, 1, 0, 15, 15, 15),
            Block.box(6, 0, 0, 10, 16, 15),
            Block.box(0, 6, 0, 16, 10, 15),
            Block.box(0, 2, 4, 16, 14, 8));

    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(1, 1, 1, 16, 15, 15),
            Block.box(1, 0, 6, 16, 16, 10),
            Block.box(1, 6, 0, 16, 10, 16),
            Block.box(8, 2, 0, 12, 14, 16));

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(HORIZONTAL_FACING)) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == shaftFace(state) || (face == front(state) && BogeyShaft.passes(world, pos.relative(face), face));
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return front(state).getAxis();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (worldIn.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = worldIn.getBlockEntity(pos);
        if (be instanceof BearingAxleBlockEntity axle) {
            player.openMenu(axle, axle::sendToMenu);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos,
                                Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        if (worldIn.isClientSide) return;

        Direction plusFace  = getPlusFace(state);
        Direction minusFace = plusFace.getOpposite();

        boolean fromPlus  = fromPos.equals(pos.relative(plusFace));
        boolean fromMinus = fromPos.equals(pos.relative(minusFace));

        BlockEntity be = worldIn.getBlockEntity(pos);
        if (!(be instanceof BearingAxleBlockEntity axle)) return;
        axle.setGearSignal(gearSignal(worldIn, pos, state));

        if (fromPlus) {
            axle.setRightRedstone(worldIn.getSignal(pos.relative(plusFace), plusFace) > 0);
        }
        if (fromMinus) {
            axle.setLeftRedstone(worldIn.getSignal(pos.relative(minusFace), minusFace) > 0);
        }
    }

    @Override
    public Class<BearingAxleBlockEntity> getBlockEntityClass() {
        return BearingAxleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BearingAxleBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.BEARING_AXLE_BE.get();
    }
}
