package com.lycoris.loconautics.content.hose.flow;

import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class HoseFlowSink implements IFluidHandler {

    public record Hop(UUID strandId, boolean forward) {
    }

    private final IFluidHandler delegate;
    private final List<Hop> hops;
    private final Level level;

    public HoseFlowSink(IFluidHandler delegate, List<Hop> hops, Level level) {
        this.delegate = delegate;
        this.hops = hops;
        this.level = level;
    }

    public static IFluidHandler unwrap(IFluidHandler handler) {
        return handler instanceof HoseFlowSink sink ? sink.delegate : handler;
    }

    @Nullable
    public static IFluidHandler tankOf(@Nullable IFluidHandler handler) {
        IFluidHandler plain = handler == null ? null : unwrap(handler);
        if (plain instanceof PressuredSink pressured && pressured.endTank() != null) {
            return pressured.endTank();
        }
        return plain;
    }

    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return markFlow(delegate.fill(resource, action), action);
    }

    public int fill(FluidStack resource, float strength, FluidAction action) {
        int moved = delegate instanceof PressuredSink pressured
                ? pressured.fill(resource, strength, action) : delegate.fill(resource, action);
        return markFlow(moved, action);
    }

    public int fillPushed(FluidStack resource, float pushRpm, FluidAction action) {
        double length = hoseLength();
        return HosePressure.reaches(pushRpm, length)
                ? fill(resource, HosePressure.strength(pushRpm, length), action) : 0;
    }

    public boolean pull(float pullRpm) {
        double length = hoseLength();
        if (!HosePressure.reaches(pullRpm, length)) {
            return false;
        }
        if (delegate instanceof PressuredSink pressured) {
            pressured.pull(HosePressure.strength(pullRpm, length));
        }
        return true;
    }

    private double hoseLength() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0.0;
        }
        SteelCableStrandManager strands = SteelCableStrandManager.get(serverLevel);
        double total = 0.0;
        for (Hop hop : hops) {
            SteelCableStrand strand = strands == null ? null : strands.get(hop.strandId());
            total += strand == null ? 0.0 : strand.restLength;
        }
        return total;
    }

    private int markFlow(int moved, FluidAction action) {
        if (moved > 0 && action.execute()) {
            long now = level.getGameTime();
            for (Hop hop : hops) {
                HoseFlowTracker.mark(hop.strandId(), hop.forward(), now);
            }
        }
        return moved;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return delegate.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return delegate.drain(maxDrain, action);
    }
}
