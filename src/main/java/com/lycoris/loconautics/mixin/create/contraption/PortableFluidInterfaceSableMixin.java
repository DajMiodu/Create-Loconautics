package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.compat.create.psi.SablePsiFluidHost;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PortableFluidInterfaceBlockEntity.class, remap = false)
public abstract class PortableFluidInterfaceSableMixin implements SablePsiFluidHost {

    @Shadow
    protected IFluidHandler capability;

    @Override
    public void loconautics$setFluidCapability(@Nullable IFluidHandler handler) {
        capability = handler == null ? new FluidTank(0) : handler;
        ((BlockEntity) (Object) this).invalidateCapabilities();
    }
}
