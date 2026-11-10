package com.lycoris.loconautics.mixin.sable.swivel;

import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SwivelBearingBlockEntity.class, remap = false)
public interface SwivelBearingAccessor {

    @Accessor("handle")
    RotaryConstraintHandle loconautics$getHandle();

    @Invoker("removeHandle")
    void loconautics$removeHandle();

    @Invoker("destroyPlate")
    void loconautics$destroyPlate();
}
