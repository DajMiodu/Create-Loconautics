package com.lycoris.loconautics.mixin.create.bogey;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractBogeyBlockEntity.class, remap = false)
public interface AbstractBogeyBlockEntityAccessor {

    @Accessor("virtualAnimation")
    LerpedFloat loconautics$getVirtualAnimation();
}
