package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.Map;

public class IndustrialGlassPipeBlock extends GlassFluidPipeBlock {

    public IndustrialGlassPipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockState toRegularPipe(LevelAccessor world, BlockPos pos, BlockState state) {
        Direction side = Direction.get(Direction.AxisDirection.POSITIVE, getAxis(state));
        Map<Direction, BooleanProperty> facingToPropertyMap = FluidPipeBlock.PROPERTY_BY_DIRECTION;
        FluidPipeBlock pipe = (FluidPipeBlock) IndustrialBlocks.PIPE.get();
        return pipe.updateBlockState(pipe.defaultBlockState()
                        .setValue(facingToPropertyMap.get(side), true)
                        .setValue(facingToPropertyMap.get(side.getOpposite()), true),
                side, null, (BlockAndTintGetter) world, pos);
    }
}
