package com.lycoris.loconautics.content.hose.flow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class PressureProbe {

    private static final int REFRESH_TICKS = 10;

    private HosePressure.Pressure value = HosePressure.Pressure.NONE;
    private long readTick = Long.MIN_VALUE / 2;

    public HosePressure.Pressure read(Level level, BlockPos endpoint, Direction inputSide) {
        long now = level.getGameTime();
        if (now - readTick >= REFRESH_TICKS) {
            value = HosePressure.read(level, endpoint, inputSide);
            readTick = now;
        }
        return value;
    }
}
