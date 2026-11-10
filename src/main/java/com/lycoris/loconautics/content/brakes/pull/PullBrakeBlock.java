package com.lycoris.loconautics.content.brakes.pull;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.common.DyedBlock;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PullBrakeBlock extends HorizontalDirectionalBlock
        implements IBE<PullBrakeBlockEntity>, IWrenchable, DyedBlock {

    public static final String BASE_COLOR = "brass";

    private static final VoxelShaper SHAPES =
            VoxelShaper.forHorizontal(Block.box(3.5, 1, 12, 12.5, 13, 16), Direction.NORTH);

    @Nullable
    private final DyeColor color;

    public PullBrakeBlock(Properties properties, @Nullable DyeColor color) {
        super(properties);
        this.color = color;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new PullBrakeBlock(properties, color));
    }

    @Nullable
    @Override
    public DyeColor getColor() {
        return color;
    }

    public String colorName() {
        return color == null ? BASE_COLOR : color.getSerializedName();
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return PullBrakeBlocks.BRAKES.get(dyed).get().defaultBlockState().setValue(FACING, state.getValue(FACING));
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return paint(stack, state, level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide && player.isLocalPlayer()) {
            withBlockEntityDo(level, pos, be -> PullBrakeHold.grab(level, player, pos, be.power()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<PullBrakeBlockEntity> getBlockEntityClass() {
        return PullBrakeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PullBrakeBlockEntity> getBlockEntityType() {
        return PullBrakeBlocks.PULL_BRAKE_BE.get();
    }
}
