package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.content.boiler.barrel.BarrelPipeConnector;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.boiler.firebox.FireboxGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class BoilerBodyInlet implements IFluidHandler {

    private static final int CACHE_TICKS = 20;

    private final Level level;
    private final BlockPos pos;

    @Nullable private BlockPos cachedController;
    private long cachedAt = Long.MIN_VALUE;

    public BoilerBodyInlet(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Nullable
    private FireboxBlockEntity controller() {
        if (level.isClientSide) {
            return null;
        }
        long now = level.getGameTime();
        if (cachedController == null || now - cachedAt >= CACHE_TICKS
                || !(level.getBlockEntity(cachedController) instanceof FireboxBlockEntity)) {
            cachedController = findController();
            cachedAt = now;
        }
        return cachedController != null && level.getBlockEntity(cachedController) instanceof FireboxBlockEntity be
                && be.getWaterTank().getCapacity() > 0 ? be : null;
    }

    @Nullable
    private BlockPos findController() {
        Set<BlockPos> seen = new HashSet<>();
        Queue<BlockPos> frontier = new ArrayDeque<>();
        seen.add(pos);
        frontier.add(pos);
        while (!frontier.isEmpty() && seen.size() <= BoilerMultiblockValidator.MAX_BODY_BLOCKS) {
            BlockPos current = frontier.poll();
            Direction.Axis axis = BarrelPipeConnector.bodyAxis(level.getBlockState(current));
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                BlockState state = level.getBlockState(next);
                if (state.is(BoilerTags.BOILER_FIREBOX)) {
                    BlockPos owner = FireboxGroups.controllerOf(level, next);
                    if (owner != null) {
                        return owner;
                    }
                } else if (axis != null && dir.getAxis() == axis && offsetFirebox(next, dir) != null) {
                    return offsetFirebox(next, dir);
                } else if (state.is(BoilerTags.BOILER_BODY) && (axis == null || dir.getAxis() == axis)
                        && seen.add(next)) {
                    frontier.add(next);
                }
            }
        }
        return null;
    }

    @Nullable
    private BlockPos offsetFirebox(BlockPos end, Direction along) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() == along.getAxis()) {
                continue;
            }
            BlockPos beside = end.relative(side);
            if (level.getBlockState(beside).is(BoilerTags.BOILER_FIREBOX)) {
                BlockPos owner = FireboxGroups.controllerOf(level, beside);
                if (owner != null) {
                    return owner;
                }
            }
        }
        return null;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        FireboxBlockEntity firebox = controller();
        return firebox == null ? FluidStack.EMPTY : firebox.getWaterTank().getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        FireboxBlockEntity firebox = controller();
        return firebox == null ? 0 : firebox.getWaterTank().getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack.getFluid() == Fluids.WATER && controller() != null;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        FireboxBlockEntity firebox = controller();
        return firebox == null ? 0 : firebox.fillFromHose(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
