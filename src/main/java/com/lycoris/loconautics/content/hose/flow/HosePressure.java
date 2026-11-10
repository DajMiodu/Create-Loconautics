package com.lycoris.loconautics.content.hose.flow;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.hose.link.HoseConnectorBlockEntity;
import com.lycoris.loconautics.content.hose.link.HoseLinks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class HosePressure {

    public static final float MIN_SPRAY_STRENGTH = 0.15f;
    private static final float REACH_TOLERANCE = 0.5f;

    private static final int MAX_LINK_DEPTH = 4;

    public record Pressure(float push, float pull) {

        public static final Pressure NONE = new Pressure(0.0f, 0.0f);

        Pressure scaled(float factor) {
            return new Pressure(push * factor, pull * factor);
        }
    }

    private record Step(BlockPos pos, int pipes) {
    }

    private HosePressure() {
    }

    public static float rpmFraction(float rpm) {
        int maxRpm = AllConfigs.server().kinetics.maxRotationSpeed.get();
        return Mth.clamp(Math.abs(rpm) / maxRpm, 0.0f, 1.0f);
    }

    public static float reach(float rpm) {
        return rpmFraction(rpm) * LoconauticsConfig.HOSE_MAX_LENGTH.get();
    }

    public static boolean reaches(float rpm, double hoseLength) {
        return reach(rpm) + REACH_TOLERANCE >= Math.min(hoseLength, LoconauticsConfig.HOSE_MAX_LENGTH.get());
    }

    public static float strength(float rpm, double hoseLength) {
        float lengthFactor = 1.0f - (float) hoseLength / LoconauticsConfig.HOSE_MAX_LENGTH.get();
        return Mth.clamp(rpmFraction(rpm) * lengthFactor, MIN_SPRAY_STRENGTH, 1.0f);
    }

    public static Pressure read(Level level, BlockPos endpoint, Direction inputSide) {
        return read(level, endpoint, inputSide, 0);
    }

    public static Pressure read(Level level, BlockPos endpoint, Direction inputSide, int linkDepth) {
        BlockPos neighbor = endpoint.relative(inputSide);
        Direction facing = inputSide.getOpposite();
        BlockEntity be = level.getBlockEntity(neighbor);
        if (be instanceof HoseConnectorBlockEntity connector) {
            return connector.plateSide() == facing && linkDepth < MAX_LINK_DEPTH
                    ? HoseLinks.pressureBehind(connector, linkDepth + 1) : Pressure.NONE;
        }
        FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, neighbor);
        PipeConnection face = pipe == null ? null : pipe.getConnection(facing);
        if (face == null || !face.hasPressure()) {
            return Pressure.NONE;
        }
        Pressure raw = new Pressure(face.getPressure().getSecond(), face.getPressure().getFirst());
        return be instanceof PumpBlockEntity ? raw : raw.scaled(distanceFactor(level, neighbor));
    }

    private static float distanceFactor(Level level, BlockPos firstPipe) {
        Queue<Step> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        int limit = FluidPropagator.getPumpRange();
        queue.add(new Step(firstPipe, 1));
        visited.add(firstPipe);
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, step.pos());
            if (pipe == null) {
                continue;
            }
            for (Direction d : FluidPropagator.getPipeConnections(level.getBlockState(step.pos()), pipe)) {
                BlockPos next = step.pos().relative(d);
                if (!level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockEntity(next) instanceof PumpBlockEntity pump) {
                    if (drives(pump, d.getOpposite())) {
                        int range = FluidPropagator.getPumpRange();
                        return Mth.clamp(1.0f - (float) step.pipes() / range, 0.0f, 1.0f);
                    }
                    continue;
                }
                if (step.pipes() < limit && visited.add(next)) {
                    queue.add(new Step(next, step.pipes() + 1));
                }
            }
        }
        return 1.0f;
    }

    private static boolean drives(PumpBlockEntity pump, Direction side) {
        FluidTransportBehaviour transport = pump.getBehaviour(FluidTransportBehaviour.TYPE);
        PipeConnection connection = transport == null ? null : transport.getConnection(side);
        return connection != null && connection.hasPressure();
    }
}
