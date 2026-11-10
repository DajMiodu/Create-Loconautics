package com.lycoris.loconautics.mixin.create.fluid;

import com.lycoris.loconautics.content.boiler.barrel.BarrelPipeConnector;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidPipeBlock.class, remap = false)
public class FluidPipeBarrelGapMixin {

    // create only ever looks one block over, the barrel octagon sits an extra block back so it never gets seen
    @Inject(method = "canConnectTo", at = @At("HEAD"), cancellable = true)
    private static void loconautics$connectAcrossBarrelGap(BlockAndTintGetter world, BlockPos neighbourPos,
                                                           BlockState neighbour, Direction direction,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (!neighbour.isAir()) return;
        BlockState behind = world.getBlockState(neighbourPos.relative(direction));
        Direction.Axis length = BarrelPipeConnector.bodyAxis(behind);
        if (length != null && length != direction.getAxis()) {
            cir.setReturnValue(true);
        }
    }
}
