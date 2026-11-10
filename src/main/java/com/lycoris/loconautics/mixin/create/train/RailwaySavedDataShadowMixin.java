package com.lycoris.loconautics.mixin.create.train;

import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.trains.RailwaySavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// a shadow saved here returns as a real create train, and electroenergetics wraps train.write so it cannot skip one
@Mixin(value = RailwaySavedData.class, remap = false)
public abstract class RailwaySavedDataShadowMixin {

    @Inject(method = "save", at = @At("HEAD"))
    private void loconautics$hideShadowTrains(CompoundTag nbt, HolderLookup.Provider registries,
                                              CallbackInfoReturnable<CompoundTag> cir) {
        CreateTrainShadow.hideForSave();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void loconautics$restoreShadowTrains(CompoundTag nbt, HolderLookup.Provider registries,
                                                 CallbackInfoReturnable<CompoundTag> cir) {
        CreateTrainShadow.restoreAfterSave();
    }
}
