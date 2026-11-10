package com.lycoris.loconautics.mixin.create.fluid;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lycoris.loconautics.content.industrial.IndustrialBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PumpBlockEntity.class, remap = false)
public class IndustrialPumpRangeMixin {

    @ModifyExpressionValue(
            method = "distributePressureTo",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/fluids/FluidPropagator;getPumpRange()I"
            )
    )
    private int loconautics$industrialRange(int original) {
        BlockEntity self = (BlockEntity) (Object) this;
        return IndustrialBlocks.pumpRange(self.getBlockState(), original);
    }
}
