package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.trainphysics.actors.TrainActorDrops;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public abstract class BlockBreakerTrainDropsMixin {

    // creates own per stack drop consumer, simulated hooks the same one so this stays a cancel, never an overwrite
    @Inject(method = "lambda$onBlockBroken$0", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$dropIntoCarriage(Vec3 vec, ItemStack stack, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (TrainActorDrops.depositInPlace(self.getLevel(), self.getBlockPos(), stack)) {
            ci.cancel();
        }
    }
}
