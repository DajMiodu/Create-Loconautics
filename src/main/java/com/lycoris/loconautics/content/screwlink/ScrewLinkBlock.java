package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public class ScrewLinkBlock extends HorizontalDirectionalBlock
        implements IBE<ScrewLinkBlockEntity>, IWrenchable, BlockSubLevelCollisionShape,
        BlockSubLevelAssemblyListener {

    public static final MapCodec<ScrewLinkBlock> CODEC = simpleCodec(ScrewLinkBlock::new);

    private static final Map<Direction, VoxelShape> SHAPES = VoxelShapes.byHorizontalFacing(
            Shapes.or(Block.box(0, 4, 13, 16, 16, 16), Block.box(3, 7, 6, 13, 13, 13)));

    // the hook is left out on purpose, a rope ball welded at its eye must not touch its own body
    private static final Map<Direction, VoxelShape> PHYSICS_SHAPES =
            VoxelShapes.byHorizontalFacing(Block.box(0, 4, 13, 16, 16, 16));

    public ScrewLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return PHYSICS_SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof ScrewLinkBlockEntity be)) {
            return InteractionResult.PASS;
        }
        return be.decouple() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    @Override
    public Class<ScrewLinkBlockEntity> getBlockEntityClass() {
        return ScrewLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ScrewLinkBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.SCREW_LINK_BE.get();
    }
}
