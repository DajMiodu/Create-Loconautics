package com.lycoris.loconautics.content.combustion;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class FuelNetwork {

    private static final int MAX_PIPES = 128;

    public record Reading(int amount, int capacity) {
        public static final Reading NONE = new Reading(0, 0);
    }

    private FuelNetwork() {}

    // reads every tank reachable through pipes from the bottom of each engine, or a tank right under it
    public static Reading read(Level level, List<BlockPos> engines, FluidStack kind) {
        Set<BlockPos> pipesSeen = new HashSet<>();
        Set<BlockPos> tanksSeen = new HashSet<>();
        int[] totals = new int[2];
        FluidStack[] fuel = {kind.isEmpty() ? FluidStack.EMPTY : kind.copyWithAmount(1)};

        for (BlockPos engine : engines) {
            BlockPos first = engine.below();
            if (!level.isLoaded(first)) continue;
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, first);
            if (pipe == null) {
                addTank(level, first, Direction.UP, tanksSeen, fuel, totals);
                continue;
            }
            if (!FluidPropagator.getPipeConnections(level.getBlockState(first), pipe).contains(Direction.UP)) continue;
            walk(level, engine, first, pipesSeen, tanksSeen, fuel, totals);
        }
        return new Reading(totals[0], totals[1]);
    }

    private static void walk(Level level, BlockPos engine, BlockPos first, Set<BlockPos> pipesSeen,
                             Set<BlockPos> tanksSeen, FluidStack[] fuel, int[] totals) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        if (pipesSeen.add(first)) queue.add(first);
        while (!queue.isEmpty() && pipesSeen.size() <= MAX_PIPES) {
            BlockPos pos = queue.poll();
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
            if (pipe == null) continue;
            for (Direction side : FluidPropagator.getPipeConnections(level.getBlockState(pos), pipe)) {
                BlockPos next = pos.relative(side);
                if (next.equals(engine) || !level.isLoaded(next)) continue;
                if (FluidPropagator.getPipe(level, next) != null) {
                    if (pipesSeen.add(next)) queue.add(next);
                } else {
                    addTank(level, next, side.getOpposite(), tanksSeen, fuel, totals);
                }
            }
        }
    }

    private static void addTank(Level level, BlockPos pos, Direction side, Set<BlockPos> tanksSeen, FluidStack[] fuel,
                                int[] totals) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null) return;
        BlockPos key = pos;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FluidTankBlockEntity tank && tank.getControllerBE() != null) {
            key = tank.getControllerBE().getBlockPos();
        }
        if (!tanksSeen.add(key)) return;

        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack held = handler.getFluidInTank(i);
            if (held.isEmpty()) {
                if (!fuel[0].isEmpty()) totals[1] += handler.getTankCapacity(i);
                continue;
            }
            if (!CombustionFuel.accepts(held)) continue;
            if (fuel[0].isEmpty()) fuel[0] = held.copyWithAmount(1);
            if (!FluidStack.isSameFluidSameComponents(fuel[0], held)) continue;
            totals[0] += held.getAmount();
            totals[1] += handler.getTankCapacity(i);
        }
    }
}
