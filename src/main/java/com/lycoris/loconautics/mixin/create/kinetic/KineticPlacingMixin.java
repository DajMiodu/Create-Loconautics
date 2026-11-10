package com.lycoris.loconautics.mixin.create.kinetic;

import com.lycoris.loconautics.common.strand.StrandTemplates;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticPlacingMixin {

    @Inject(method = "read", at = @At("TAIL"))
    private void loconautics$dropSavedNetwork(CompoundTag tag, HolderLookup.Provider registries,
                                              boolean clientPacket, CallbackInfo ci) {
        if (!clientPacket && StrandTemplates.isPlacing()) {
            ((KineticBlockEntity) (Object) this).clearKineticInformation();
        }
    }
}
