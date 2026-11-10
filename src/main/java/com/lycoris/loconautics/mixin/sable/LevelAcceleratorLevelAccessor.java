package com.lycoris.loconautics.mixin.sable;

import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LevelAccelerator.class, remap = false)
public interface LevelAcceleratorLevelAccessor {
    @Accessor("level")
    Level loconautics$level();
}
