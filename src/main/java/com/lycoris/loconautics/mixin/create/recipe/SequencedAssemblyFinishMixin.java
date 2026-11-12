package com.lycoris.loconautics.mixin.create.recipe;

import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SequencedAssemblyRecipe.class, remap = false)
public abstract class SequencedAssemblyFinishMixin {

    @Inject(method = "advance", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/processing/sequenced/SequencedAssemblyRecipe;rollResult(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/ItemStack;"), remap = false, require = 0)
    private void loconautics$finished(CallbackInfoReturnable<?> cir) {
        TrainAdvancements.sequencedAssemblyFinished();
    }
}
