package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.common.dock.HorizontalDockPoint;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class HatchWaterSocket {

    private final BlockEntity physical;
    private final Supplier<Direction> facing;
    private final BooleanSupplier raised;
    private final java.util.function.DoubleSupplier dockSeatOffset;
    private final FluidTank tank;
    private boolean resolving;

    public HatchWaterSocket(BlockEntity physical, Supplier<Direction> facing, BooleanSupplier raised,
                            java.util.function.DoubleSupplier dockSeatOffset, FluidTank tank) {
        this.physical = physical;
        this.facing = facing;
        this.raised = raised;
        this.dockSeatOffset = dockSeatOffset;
        this.tank = tank;
    }

    private Direction dockFace() {
        return facing.get().getOpposite();
    }

    public Vec3 dockAnchorLocal() {
        double out = dockSeatOffset.getAsDouble() + (raised.getAsBoolean() ? 0.5 : 0.0);
        return HorizontalDockPoint.anchorLocal(physical, dockFace(), out);
    }

    public Vec3 getDockPoint() {
        return HorizontalDockPoint.worldPoint(physical, dockAnchorLocal());
    }

    public Quaterniond getDockOrientation() {
        return HorizontalDockPoint.orientation(dockFace());
    }

    @Nullable
    public IFluidHandler getDockedFluidHandler() {
        Level level = physical.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        if (resolving) {
            return tank;
        }
        Direction into = facing.get();
        resolving = true;
        try {
            IFluidHandler downstream = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    physical.getBlockPos().relative(into), into.getOpposite());
            return downstream != null ? downstream : tank;
        } finally {
            resolving = false;
        }
    }

    private IFluidHandler waterTarget() {
        IFluidHandler handler = getDockedFluidHandler();
        return handler != null ? handler : tank;
    }

    public int getWaterAmount() {
        IFluidHandler handler = waterTarget();
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (stack.getFluid() == Fluids.WATER) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public int getWaterCapacity() {
        IFluidHandler handler = waterTarget();
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (stack.getFluid() == Fluids.WATER) {
                total += handler.getTankCapacity(i);
            } else if (stack.isEmpty() && handler.isFluidValid(i, new FluidStack(Fluids.WATER, 1))) {
                total += handler.getTankCapacity(i);
            }
        }
        return total;
    }

    public int drawWater(int millibuckets) {
        if (millibuckets <= 0) {
            return 0;
        }
        return waterTarget().drain(new FluidStack(Fluids.WATER, millibuckets),
                IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    public void returnWater(int millibuckets) {
        if (millibuckets <= 0) {
            return;
        }
        waterTarget().fill(new FluidStack(Fluids.WATER, millibuckets), IFluidHandler.FluidAction.EXECUTE);
    }
}
