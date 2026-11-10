package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.trainphysics.actors.TrainActorDrops;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SawBlockEntity.class, remap = false)
public abstract class SawTrainDropsMixin {

    @Inject(method = "dropItemFromCutTree", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$dropIntoCarriage(BlockPos pos, ItemStack stack, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (TrainActorDrops.depositInPlace(self.getLevel(), self.getBlockPos(), stack)) {
            ci.cancel();
        }
    }
}
