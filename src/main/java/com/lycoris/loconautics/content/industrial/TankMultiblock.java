package com.lycoris.loconautics.content.industrial;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TankMultiblock {

    public enum Fit { OK, TOO_BIG, MIXED_FLUIDS }

    public record Group(List<BlockPos> cells, boolean complete) {}

    private static final Comparator<BlockPos> CONTROLLER_ORDER = Comparator.comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getX())
            .thenComparingInt(pos -> pos.getZ());

    private static final int STATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private TankMultiblock() {}

    public static boolean isTank(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(IndustrialBlocks.TANK.get());
    }

    public static Group collect(Level level, Collection<BlockPos> starts, Set<BlockPos> virtual) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : starts) {
            if (seen.add(start)) queue.add(start);
        }

        List<BlockPos> cells = new ArrayList<>();
        boolean complete = true;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            cells.add(current);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (seen.contains(next)) continue;
                if (!virtual.contains(next)) {
                    if (!level.isLoaded(next)) {
                        complete = false;
                        continue;
                    }
                    if (!isTank(level, next)) continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        return new Group(cells, complete);
    }

    public static boolean fitsSize(Collection<BlockPos> cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : cells) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int[] spans = {maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
        Arrays.sort(spans);
        int maxLength = Math.max(LoconauticsConstants.INDUSTRIAL_TANK_MAX_WIDTH, FluidTankBlockEntity.getMaxHeight());
        return spans[0] <= LoconauticsConstants.INDUSTRIAL_TANK_MAX_WIDTH
                && spans[1] <= LoconauticsConstants.INDUSTRIAL_TANK_MAX_WIDTH
                && spans[2] <= maxLength;
    }

    public static Fit checkPlacement(Level level, Collection<BlockPos> newCells) {
        Set<BlockPos> virtual = new HashSet<>(newCells);
        Group group = collect(level, newCells, virtual);
        if (!fitsSize(group.cells())) return Fit.TOO_BIG;

        FluidStack seen = FluidStack.EMPTY;
        for (BlockPos pos : group.cells()) {
            if (virtual.contains(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof IndustrialTankBlockEntity cell) || !cell.isController()) {
                continue;
            }
            FluidStack fluid = cell.getFluid();
            if (fluid.isEmpty()) continue;
            if (seen.isEmpty()) {
                seen = fluid;
            } else if (!FluidStack.isSameFluidSameComponents(seen, fluid)) {
                return Fit.MIXED_FLUIDS;
            }
        }
        return Fit.OK;
    }

    public static void reform(ServerLevel level, BlockPos pos) {
        Group group = collect(level, List.of(pos), Set.of());
        if (!group.complete()) return;

        List<BlockPos> cells = group.cells();
        if (!fitsSize(cells)) {
            level.destroyBlock(pos, true);
            return;
        }

        BlockPos head = controllerOf(cells);
        boolean consistent = true;
        boolean window = false;
        boolean windowMixed = false;
        boolean firstWindow = level.getBlockState(cells.get(0)).getValue(IndustrialTankBlock.WINDOW);
        FluidStack merged = FluidStack.EMPTY;
        for (BlockPos cell : cells) {
            if (!(level.getBlockEntity(cell) instanceof IndustrialTankBlockEntity be)) return;
            boolean cellWindow = level.getBlockState(cell).getValue(IndustrialTankBlock.WINDOW);
            windowMixed |= cellWindow != firstWindow;
            window |= cellWindow;

            if (be.isController()) {
                consistent &= cell.equals(head);
                merged = merge(merged, be.getFluid());
            } else {
                consistent &= head.equals(be.getControllerPos());
            }
        }
        if (level.getBlockEntity(head) instanceof IndustrialTankBlockEntity headBE) {
            consistent &= headBE.isController() && headBE.getSize() == cells.size();
        }
        if (consistent && !windowMixed) return;

        apply(level, cells, merged, window);
    }

    public static void onRemoved(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IndustrialTankBlockEntity removed)) return;
        IndustrialTankBlockEntity head = removed.getControllerBE();
        FluidStack total = head == null ? FluidStack.EMPTY : head.getFluid().copy();

        List<List<BlockPos>> pieces = new ArrayList<>();
        Set<BlockPos> claimed = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (claimed.contains(next) || !isTank(level, next)) continue;
            Group group = collect(level, List.of(next), Set.of());
            claimed.addAll(group.cells());
            pieces.add(group.cells());
        }
        if (pieces.isEmpty()) return;

        boolean gas = !total.isEmpty() && total.getFluid().getFluidType().isLighterThanAir();
        Comparator<List<BlockPos>> order = gas
                ? Comparator.comparingInt((List<BlockPos> piece) -> highest(piece)).reversed()
                : Comparator.comparingInt(TankMultiblock::lowest);
        pieces.sort(order);

        FluidStack remaining = total.copy();
        for (List<BlockPos> piece : pieces) {
            int amount = Math.min(remaining.getAmount(), piece.size() * IndustrialTankBlockEntity.cellCapacity());
            FluidStack share = amount <= 0 ? FluidStack.EMPTY : remaining.copy();
            if (!share.isEmpty()) {
                share.setAmount(amount);
                remaining.shrink(amount);
            }
            boolean window = false;
            for (BlockPos cell : piece) {
                window |= level.getBlockState(cell).getValue(IndustrialTankBlock.WINDOW);
            }
            apply(level, piece, share, window);
        }
    }

    public static void toggleWindows(Level level, BlockPos pos) {
        Group group = collect(level, List.of(pos), Set.of());
        boolean window = !level.getBlockState(pos).getValue(IndustrialTankBlock.WINDOW);
        for (BlockPos cell : group.cells()) {
            setWindow(level, cell, window);
        }
    }

    private static void apply(Level level, List<BlockPos> cells, FluidStack fluid, boolean window) {
        BlockPos head = controllerOf(cells);
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            minY = Math.min(minY, cell.getY());
            maxY = Math.max(maxY, cell.getY());
        }
        int[] layers = new int[maxY - minY + 1];

        for (BlockPos cell : cells) {
            layers[cell.getY() - minY]++;
            setWindow(level, cell, window);
            if (cell.equals(head)) continue;
            if (level.getBlockEntity(cell) instanceof IndustrialTankBlockEntity be) be.becomeMember(head);
        }
        if (level.getBlockEntity(head) instanceof IndustrialTankBlockEntity headBE) {
            headBE.configureController(cells.size(), minY, layers, fluid);
        }
    }

    private static void setWindow(Level level, BlockPos pos, boolean window) {
        BlockState state = level.getBlockState(pos);
        if (state.getValue(IndustrialTankBlock.WINDOW) == window) return;
        level.setBlock(pos, state.setValue(IndustrialTankBlock.WINDOW, window), STATE_FLAGS);
    }

    private static FluidStack merge(FluidStack merged, FluidStack next) {
        if (next.isEmpty()) return merged;
        if (merged.isEmpty()) return next.copy();
        if (FluidStack.isSameFluidSameComponents(merged, next)) {
            FluidStack sum = merged.copy();
            sum.grow(next.getAmount());
            return sum;
        }
        return next.getAmount() > merged.getAmount() ? next.copy() : merged;
    }

    private static BlockPos controllerOf(List<BlockPos> cells) {
        return cells.stream().min(CONTROLLER_ORDER).orElseThrow(() -> new IllegalStateException("empty tank group"));
    }

    private static int lowest(List<BlockPos> piece) {
        return piece.stream().mapToInt(pos -> pos.getY()).min().orElse(0);
    }

    private static int highest(List<BlockPos> piece) {
        return piece.stream().mapToInt(pos -> pos.getY()).max().orElse(0);
    }
}
