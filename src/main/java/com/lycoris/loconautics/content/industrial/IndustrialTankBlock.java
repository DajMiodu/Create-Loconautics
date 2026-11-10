package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public class IndustrialTankBlock extends Block implements IWrenchable, IBE<IndustrialTankBlockEntity> {

    public static final EnumProperty<Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty WINDOW = BooleanProperty.create("window");

    public IndustrialTankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Axis.Y).setValue(WINDOW, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, WINDOW);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
        BlockState state = defaultBlockState();
        if (against.is(this)) {
            return state.setValue(AXIS, against.getValue(AXIS)).setValue(WINDOW, against.getValue(WINDOW));
        }
        return state.setValue(AXIS, face.getAxis());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        AdvancementBehaviour.setPlacedBy(level, pos, placer);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (oldState.is(this) || level.isClientSide) return;
        level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.is(this)) return;
        TankMultiblock.reform(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) return;
        if (!level.isClientSide) TankMultiblock.onRemoved(level, pos);
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) TankMultiblock.toggleWindows(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty() || !player.isCreative()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        IndustrialTankBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return ItemInteractionResult.FAIL;
        IndustrialTankBlockEntity head = be.getControllerBE();
        if (head == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        FluidStack before = head.getFluid().copy();
        boolean emptied = FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, be);
        boolean filled = !emptied && FluidHelper.tryFillItemFromBE(level, player, hand, stack, be);
        if (!emptied && !filled) {
            if (GenericItemEmptying.canItemBeEmptied(level, stack) || GenericItemFilling.canItemBeFilled(level, stack)) {
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            FluidStack sound = emptied ? head.getFluid() : before;
            SoundEvent event = emptied ? FluidHelper.getEmptySound(sound) : FluidHelper.getFillSound(sound);
            if (event != null) level.playSound(null, pos, event, SoundSource.BLOCKS, 0.5f, 1f);
            head.sendDataNow();
            head.setChanged();
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof IndustrialTankBlockEntity tank ? tank.getLuminosity() : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getBlockEntityOptional(level, pos).map(IndustrialTankBlockEntity::getControllerBE)
                .map(head -> ComparatorUtil.fractionToRedstoneLevel(head.fillState()))
                .orElse(0);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        if (entity != null && entity.getPersistentData().contains("SilenceTankSound")) {
            return FluidTankBlock.SILENCED_METAL;
        }
        return super.getSoundType(state, level, pos, entity);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        if (!quarter) return state;
        return switch (state.getValue(AXIS)) {
            case X -> state.setValue(AXIS, Axis.Z);
            case Z -> state.setValue(AXIS, Axis.X);
            default -> state;
        };
    }

    @Override
    public Class<IndustrialTankBlockEntity> getBlockEntityClass() {
        return IndustrialTankBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends IndustrialTankBlockEntity> getBlockEntityType() {
        return IndustrialBlocks.TANK_BE.get();
    }
}
