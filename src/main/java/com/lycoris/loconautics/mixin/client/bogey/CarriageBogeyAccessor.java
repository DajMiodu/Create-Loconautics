package com.lycoris.loconautics.mixin.client.bogey;

import com.simibubi.create.content.trains.entity.CarriageBogey;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CarriageBogey.class, remap = false)
public interface CarriageBogeyAccessor {
    @Accessor("yaw")
    LerpedFloat loconautics$getYaw();

    @Accessor("pitch")
    LerpedFloat loconautics$getPitch();

    @Accessor("isLeading")
    boolean loconautics$isLeading();
}
