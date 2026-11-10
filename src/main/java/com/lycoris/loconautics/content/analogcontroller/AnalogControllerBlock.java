package com.lycoris.loconautics.content.analogcontroller;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.lycoris.loconautics.common.DyedBlock;
import com.lycoris.loconautics.common.ShiftPlacement;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalogControllerBlock extends HorizontalDirectionalBlock implements IWrenchable, DyedBlock, EntityBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger("AnalogControllerBlock");

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private final DyeColor color;

    public AnalogControllerBlock(BlockBehaviour.Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
        this.registerDefaultState(
                this.defaultBlockState()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(POWER, 0)
                        .setValue(ACTIVE, false)
                        .setValue(POWERED, false)
        );
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new AnalogControllerBlock(properties, color));
    }

    @Override
    public DyeColor getColor() {
        return color;
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return AnalogControllerBlocks.CONTROLLERS.get(dyed).get().defaultBlockState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(POWER, state.getValue(POWER))
                .setValue(ACTIVE, state.getValue(ACTIVE))
                .setValue(POWERED, state.getValue(POWERED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, POWER, ACTIVE, POWERED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ShiftPlacement.facing(ctx));
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(POWER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.CONTROLS.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.CONTROLS_COLLISION.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (stack.getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        ItemInteractionResult painted = paint(stack, state, level, pos, player);
        if (painted != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return painted;

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                AnalogControllerBlockEntity ace = resolveBlockEntity(level, pos);
                if (ace != null) {
                    sp.openMenu(ace, ace::sendToMenu);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            AnalogControllerBlockEntity ace = resolveBlockEntity(level, pos);
            if (ace != null) {
                ace.toggleUser(player);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    private static AnalogControllerBlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AnalogControllerBlockEntity ace) return ace;

        dev.ryanhcode.sable.sublevel.SubLevel subLevel =
                dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
        if (subLevel != null) {
            be = subLevel.getLevel().getBlockEntity(pos);
            if (be instanceof AnalogControllerBlockEntity ace) return ace;
        }
        LOGGER.warn("[analog-block] resolveBlockEntity returned null for pos={}", pos);
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide
                || !com.lycoris.loconautics.content.frequency.FrequencyBindUtil.isBound(stack)
                || !(resolveBlockEntity(level, pos) instanceof AnalogControllerBlockEntity be)) {
            return;
        }
        var reg = level.registryAccess();
        ItemStack[] left = com.lycoris.loconautics.content.frequency.FrequencyBindUtil.read(stack, reg, "Left");
        if (left != null) be.setLeftFrequency(left[0], left[1]);
        ItemStack[] right = com.lycoris.loconautics.content.frequency.FrequencyBindUtil.read(stack, reg, "Right");
        if (right != null) be.setRightFrequency(right[0], right[1]);
        ItemStack[] speed = com.lycoris.loconautics.content.frequency.FrequencyBindUtil.read(stack, reg, "Speed");
        if (speed != null) be.setFrequency(speed[0], speed[1]);
        ItemStack[] dir = com.lycoris.loconautics.content.frequency.FrequencyBindUtil.read(stack, reg, "Direction");
        if (dir != null) be.setBackwardFrequency(dir[0], dir[1]);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnalogControllerBlockEntity(AnalogControllerBlocks.CONTROLLER_BE.get(), pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return type == AnalogControllerBlocks.CONTROLLER_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<AnalogControllerBlockEntity>) AnalogControllerBlockEntity::tick
                : null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            AnalogControllerBlockEntity ace = resolveBlockEntity(level, pos);
            if (ace != null) {
                ace.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}