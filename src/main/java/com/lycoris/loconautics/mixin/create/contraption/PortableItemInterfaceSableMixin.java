package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.compat.create.psi.SablePsiItemHost;
import com.simibubi.create.content.contraptions.actors.psi.PortableItemInterfaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PortableItemInterfaceBlockEntity.class, remap = false)
public abstract class PortableItemInterfaceSableMixin implements SablePsiItemHost {

    @Shadow
    protected IItemHandlerModifiable capability;

    @Override
    public void loconautics$setItemCapability(@Nullable IItemHandlerModifiable handler) {
        capability = handler == null ? new ItemStackHandler(0) : handler;
        ((BlockEntity) (Object) this).invalidateCapabilities();
    }
}
