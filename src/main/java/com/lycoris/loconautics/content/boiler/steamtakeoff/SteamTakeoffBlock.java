package com.lycoris.loconautics.content.boiler.steamtakeoff;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SteamTakeoffBlock extends RotatedPillarKineticBlock implements IBE<SteamTakeoffBlockEntity> {

    public SteamTakeoffBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public Class<SteamTakeoffBlockEntity> getBlockEntityClass() {
        return SteamTakeoffBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamTakeoffBlockEntity> getBlockEntityType() {
        return BoilerBlocks.STEAM_TAKEOFF_BE.get();
    }
}
