package com.lycoris.loconautics.content.ball;

import com.lycoris.loconautics.common.SubLevelAssemblyUtil;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BallBlock extends Block implements IBE<BallBlockEntity>, BlockSubLevelCollisionShape {

    private static final VoxelShape SHAPE = Shapes.block();

    public BallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            SubLevelAssemblyUtil.assembleSingleBlock(serverLevel, pos, "[ball]");
        }
    }

    @Override
    public Class<BallBlockEntity> getBlockEntityClass() {
        return BallBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BallBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.BALL_BE.get();
    }
}
