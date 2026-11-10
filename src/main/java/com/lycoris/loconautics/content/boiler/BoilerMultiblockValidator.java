package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlock;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class BoilerMultiblockValidator {

    public static final int MAX_BODY_BLOCKS = 256;

    private BoilerMultiblockValidator() {}

    public record Scan(@org.jetbrains.annotations.Nullable BoilerStructure structure,
                       @org.jetbrains.annotations.Nullable String missingKey) {}

    public static Scan scan(Level level, BlockPos controllerPos) {
        if (level == null || level.isClientSide) return new Scan(null, null);

        Set<BlockPos> fireboxes = com.lycoris.loconautics.content.boiler.firebox.FireboxGroups
                .members(level, controllerPos);

        float fireboxHeat = 0f;
        for (BlockPos pos : fireboxes) {
            fireboxHeat += BoilerContributions.fireboxHeatOf(level.getBlockState(pos));
        }

        Set<BlockPos> bodySet = new HashSet<>();
        Queue<BlockPos> frontier = new ArrayDeque<>();
        for (BlockPos firebox : fireboxes) {
            for (Direction dir : Direction.values()) {
                seedBody(level, firebox.relative(dir), dir.getAxis(), true, bodySet, frontier);
                for (Direction side : Direction.values()) {
                    if (side.getAxis() != dir.getAxis()) {
                        seedBody(level, firebox.relative(dir).relative(side), dir.getAxis(), false, bodySet, frontier);
                    }
                }
            }
        }

        List<BlockPos> bodies = new ArrayList<>();
        Direction.Axis bodyAxis = null;
        BlockPos stack = null;
        BlockPos injector = null;
        BlockPos takeoff = null;
        BlockPos safetyValve = null;
        float boilerCapacity = 0f;
        int waterCapacity = 0;

        while (!frontier.isEmpty() && bodies.size() < MAX_BODY_BLOCKS) {
            BlockPos current = frontier.poll();
            BlockState currentState = level.getBlockState(current);
            Direction.Axis axis = bodyAxis(currentState);
            if (axis != null) {
                if (bodyAxis != null && bodyAxis != axis) return new Scan(null, "align_boiler_body");
                bodyAxis = axis;
            }
            bodies.add(current);
            boilerCapacity += BoilerContributions.boilerCapacityOf(currentState);
            waterCapacity += BoilerContributions.waterCapacityOf(currentState);

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                BlockState ns = level.getBlockState(neighbor);
                boolean alongRun = axis == null || dir.getAxis() == axis;

                if (ns.is(BoilerTags.BOILER_BODY)) {
                    if (alongRun && bodySet.add(neighbor)) frontier.add(neighbor);
                } else if (stack == null && dir == Direction.UP && ns.is(BoilerTags.SMOKESTACK)) {
                    stack = neighbor;
                } else if (injector == null && ns.is(BoilerTags.HATCHES)) {
                    injector = neighbor;
                } else if (takeoff == null && ns.getBlock() instanceof SteamTakeoffBlock) {
                    takeoff = neighbor;
                } else if (safetyValve == null && ns.getBlock() instanceof SafetyValveBlock) {
                    safetyValve = neighbor;
                }
            }
        }

        if (!frontier.isEmpty() || spanAlong(bodies, bodyAxis) > LoconauticsConfig.BOILER_MAX_LENGTH.get()) {
            return new Scan(null, "too_large");
        }
        if (bodies.size() < BoilerStructure.MIN_BODY_BLOCKS) return new Scan(null, "add_boiler_body");

        return new Scan(new BoilerStructure(
                controllerPos,
                List.copyOf(fireboxes),
                List.copyOf(bodies),
                stack,
                injector,
                takeoff,
                safetyValve,
                scanConnectors(level, bodies),
                fireboxHeat,
                boilerCapacity,
                waterCapacity
        ), null);
    }

    private static void seedBody(Level level, BlockPos pos, Direction.Axis reach, boolean flush,
                                 Set<BlockPos> bodySet, Queue<BlockPos> frontier) {
        BlockState state = level.getBlockState(pos);
        Direction.Axis axis = bodyAxis(state);
        boolean fits = flush ? axis == null || axis == reach : axis == reach;
        if (state.is(BoilerTags.BOILER_BODY) && fits && bodySet.add(pos)) {
            frontier.add(pos);
        }
    }

    private static int spanAlong(List<BlockPos> bodies, Direction.Axis axis) {
        if (axis == null || bodies.isEmpty()) return bodies.size();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (BlockPos pos : bodies) {
            int at = pos.get(axis);
            min = Math.min(min, at);
            max = Math.max(max, at);
        }
        return max - min + 1;
    }

    private static Direction.Axis bodyAxis(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING).getAxis();
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return state.getValue(BlockStateProperties.AXIS);
        }
        return null;
    }

    private static List<BlockPos> scanConnectors(Level level, List<BlockPos> bodies) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos body : bodies) {
            Direction.Axis length = com.lycoris.loconautics.content.boiler.barrel.BarrelPipeConnector
                    .bodyAxis(level.getBlockState(body));
            if (length == null) continue;
            for (Direction face : Direction.values()) {
                BlockState beside = level.getBlockState(body.relative(face));
                if (beside.getBlock() instanceof com.lycoris.loconautics.content.hose.link.HoseConnectorBlock
                        && beside.getValue(com.lycoris.loconautics.content.hose.link.HoseConnectorBlock.FACING)
                        == face.getOpposite()) {
                    found.add(body.relative(face));
                    continue;
                }
                if (face.getAxis() == length) continue;
                if (com.lycoris.loconautics.content.boiler.barrel.BarrelPipeConnector
                        .ensureConnector(level, body, face)) {
                    found.add(body.relative(face));
                }
            }
        }
        return List.copyOf(found);
    }

}
