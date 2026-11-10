package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HoseConnectorBlock extends Block implements IBE<HoseConnectorBlockEntity>, IWrenchable, BlockSubLevelAssemblyListener,
        BlockSubLevelCollisionShape {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShaper SHAPES = VoxelShaper.forDirectional(Shapes.or(
            box(3, 3, 0, 13, 13, 2),
            box(4, 4, 1, 12, 12, 5),
            box(2.95, 2.95, 5, 13.05, 13.05, 8.1),
            box(4.95, 4.95, 7.95, 11.05, 11.05, 10.95)), Direction.NORTH);

    private static final VoxelShaper PHYSICS_SHAPES = VoxelShaper.forDirectional(
            box(3, 3, 0, 13, 13, 0.25), Direction.NORTH);

    public HoseConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(shapeSide(state.getValue(FACING)));
    }

    private static Direction shapeSide(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? facing.getOpposite() : facing;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return PHYSICS_SHAPES.get(shapeSide(state.getValue(FACING)));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        ServerPlayer player = context.getPlayer() instanceof ServerPlayer sp ? sp : null;
        if (level.getBlockEntity(pos) != null && HoseLinks.detachNewest(level, level.getBlockEntity(pos), player)) {
            return InteractionResult.SUCCESS;
        }
        level.destroyBlock(pos, player == null || !player.isCreative());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    @Override
    public Class<HoseConnectorBlockEntity> getBlockEntityClass() {
        return HoseConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HoseConnectorBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.HOSE_CONNECTOR_BE.get();
    }
}
