package com.lycoris.loconautics.mixin.client.rope;

import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RopeWinchBlockEntity.class, remap = false)
public interface RopeWinchBlockEntityAccessor {

    @Accessor("clientAngle")
    LerpedFloat loconautics$getClientAngle();
}