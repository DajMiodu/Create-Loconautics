package com.lycoris.loconautics.content.hose.flow;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;

public final class HoseSourceProbe {

    private static final int REFRESH_TICKS = 10;
    private static final int MAX_PIPES = 128;

    private final Set<IFluidHandler> sources = Collections.newSetFromMap(new IdentityHashMap<>());
    private long readTick = Long.MIN_VALUE / 2;

    public boolean feeds(Level level, BlockPos endpoint, Direction inputSide, @Nullable IFluidHandler sink) {
        IFluidHandler tank = HoseFlowSink.tankOf(sink);
        if (tank == null) {
            return false;
        }
        long now = level.getGameTime();
        if (now - readTick >= REFRESH_TICKS) {
            refresh(level, endpoint, inputSide);
            readTick = now;
        }
        return sources.contains(tank);
    }

    private void refresh(Level level, BlockPos endpoint, Direction inputSide) {
        sources.clear();
        BlockPos first = endpoint.relative(inputSide);
        if (FluidPropagator.getPipe(level, first) == null) {
            addHandler(level, first, inputSide.getOpposite());
            return;
        }
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(first);
        seen.add(first);
        while (!queue.isEmpty() && seen.size() <= MAX_PIPES) {
            BlockPos pos = queue.poll();
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
            if (pipe == null) {
                continue;
            }
            for (Direction d : FluidPropagator.getPipeConnections(level.getBlockState(pos), pipe)) {
                BlockPos next = pos.relative(d);
                if (next.equals(endpoint) || !level.isLoaded(next) || !seen.add(next)) {
                    continue;
                }
                if (FluidPropagator.getPipe(level, next) != null) {
                    queue.add(next);
                } else {
                    addHandler(level, next, d.getOpposite());
                }
            }
        }
    }

    private void addHandler(Level level, BlockPos pos, Direction side) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler != null) {
            sources.add(handler);
        }
    }
}
