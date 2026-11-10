package com.lycoris.loconautics.mixin.create.bogey;

import com.lycoris.loconautics.common.BogeyGroundDrop;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractBogeyBlock.class)
public abstract class BogeyGroundShapeMixin {

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BogeyGroundDrop.of(level.getBlockEntity(pos)) > 0.0f ? BogeyGroundDrop.SHAPE : BogeyGroundDrop.RAISED_SHAPE;
    }
}
