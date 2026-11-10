package com.lycoris.loconautics.common.dock;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

public interface IDockable {

    Vec3 getDockPoint();

    Quaterniond getDockOrientation();

    default double dockRadius() {
        return 1.25;
    }

    default double dockSeatOffset() {
        return 10.0 / 16.0;
    }

    boolean canDock();

    default boolean isDockPresent() {
        return true;
    }

    void onDocked();

    void onUndocked();

    @Nullable
    IFluidHandler getDockedFluidHandler();

    @Nullable
    default IItemHandler getDockedItemHandler() {
        return null;
    }

    default boolean isDockLocked() {
        return false;
    }

    default boolean allowsItem(ItemStack stack) {
        return true;
    }
}
