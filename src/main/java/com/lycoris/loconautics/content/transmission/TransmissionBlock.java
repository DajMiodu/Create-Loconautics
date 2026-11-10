package com.lycoris.loconautics.content.transmission;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;


public class TransmissionBlock extends AbstractEncasedShaftBlock
        implements IBE<TransmissionBlockEntity> {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final BooleanProperty PLUS_SIDE = BooleanProperty.create("plus_side");

    public TransmissionBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.defaultBlockState()
                        .setValue(POWERED, false)
                        .setValue(PLUS_SIDE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, PLUS_SIDE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context)
                .setValue(POWERED, false)
                .setValue(PLUS_SIDE, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide
                || !com.lycoris.loconautics.content.frequency.FrequencyBindUtil.isBound(stack)
                || !(level.getBlockEntity(pos) instanceof TransmissionBlockEntity be)) {
            return;
        }
        var reg = level.registryAccess();
        net.minecraft.world.item.ItemStack[] speed =
                com.lycoris.loconautics.content.frequency.FrequencyBindUtil.read(stack, reg, "Speed");
        if (speed != null) be.setSpeedFrequency(speed[0], speed[1]);
        net.minecraft.world.item.ItemStack[] dir =
                com.lycoris.loconautics.content.frequency.FrequencyBindUtil.read(stack, reg, "Direction");
        if (dir != null) be.setDirectionFrequency(dir[0], dir[1]);
    }

    private static final VoxelShape Z_SHAPE = Shapes.or(
            Block.box(0, 0, 9, 16, 16, 16),
            Block.box(1, 1, 2, 15, 15, 9),
            Block.box(0, 0, 0, 16, 16, 2));

    private static final VoxelShape X_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 7, 16, 16),
            Block.box(7, 1, 1, 14, 15, 15),
            Block.box(14, 0, 0, 16, 16, 16));

    private static final VoxelShape Y_SHAPE = Shapes.or(
            Block.box(0, 9, 0, 16, 16, 16),
            Block.box(1, 2, 1, 15, 9, 15),
            Block.box(0, 0, 0, 16, 2, 16));

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BlockStateProperties.AXIS)) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    public Class<TransmissionBlockEntity> getBlockEntityClass() {
        return TransmissionBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TransmissionBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.TRANSMISSION_BE.get();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (worldIn.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = worldIn.getBlockEntity(pos);
        if (be instanceof TransmissionBlockEntity tbe) {
            player.openMenu(tbe, tbe::sendToMenu);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos,
                                Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        if (worldIn.isClientSide) return;

        Direction.Axis axis     = state.getValue(BlockStateProperties.AXIS);
        boolean        plusSide = state.getValue(PLUS_SIDE);
        Direction plusFace  = getPlusFace(axis, plusSide);
        Direction minusFace = plusFace.getOpposite();

        boolean fromPlus  = fromPos.equals(pos.relative(plusFace));
        boolean fromMinus = fromPos.equals(pos.relative(minusFace));
        if (!fromPlus && !fromMinus) return;

        BlockEntity be = worldIn.getBlockEntity(pos);
        if (!(be instanceof TransmissionBlockEntity tbe)) return;

        if (fromPlus) {
            int speedSignal = worldIn.getSignal(pos.relative(plusFace), plusFace.getOpposite());
            tbe.setRedstonePower(speedSignal);
        }
        if (fromMinus) {
            boolean dirSignal = worldIn.getSignal(pos.relative(minusFace), minusFace.getOpposite()) > 0;
            tbe.setDirectionActive(dirSignal);
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return false;
    }

    public static Direction getPlusFace(Direction.Axis axis, boolean plusSide) {
        Direction base = switch (axis) {
            case Z -> Direction.WEST;
            case X -> Direction.NORTH;
            case Y -> Direction.WEST;
        };
        return plusSide ? base.getOpposite() : base;
    }

    public static Direction getPlusFace(BlockState state) {
        return getPlusFace(
                state.getValue(BlockStateProperties.AXIS),
                state.getValue(PLUS_SIDE));
    }

    public static Direction getMinusFace(BlockState state) {
        return getPlusFace(state).getOpposite();
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        Direction.Axis currentAxis = state.getValue(BlockStateProperties.AXIS);
        if (targetedFace.getAxis() == currentAxis) {
            return state.cycle(PLUS_SIDE);
        }
        return super.getRotatedBlockState(state, targetedFace).setValue(PLUS_SIDE, false);
    }

    public void detachKinetics(Level worldIn, BlockPos pos) {
        BlockEntity be = worldIn.getBlockEntity(pos);
        if (!(be instanceof KineticBlockEntity kbe)) return;
        RotationPropagator.handleRemoved(worldIn, pos, kbe);
    }
}
