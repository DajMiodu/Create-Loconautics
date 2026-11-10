package com.lycoris.loconautics.mixin.sable.physicsstaff;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler$DragSession", remap = false)
public interface PhysicsStaffDragSessionAccessor {

    @Accessor("subLevel")
    ServerSubLevel loconautics$getSubLevel();
}
