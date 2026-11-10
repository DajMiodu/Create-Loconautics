package com.lycoris.loconautics.common.turntable;

import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class TurntableSignalHold {

    private static final int CALM_TICKS = 20;
    private static final String KEY = "BoundSignals";

    private final List<BlockPos> bound = new ArrayList<>();
    private int calmTicks;

    public boolean isEmpty() {
        return bound.isEmpty();
    }

    public List<BlockPos> positions() {
        return List.copyOf(bound);
    }

    public boolean bind(BlockPos signal) {
        if (bound.contains(signal)) {
            return false;
        }
        bound.add(signal.immutable());
        return true;
    }

    public void copyFrom(TurntableSignalHold other) {
        bound.clear();
        bound.addAll(other.bound);
        calmTicks = other.calmTicks;
    }

    public boolean tick(Level level, boolean busy) {
        if (bound.isEmpty()) {
            return false;
        }
        calmTicks = busy ? 0 : Math.min(calmTicks + 1, CALM_TICKS);
        boolean red = calmTicks < CALM_TICKS;
        boolean dropped = bound.removeIf(pos -> {
            boolean gone = level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof SignalBlockEntity);
            if (gone) {
                TurntableSignals.hold(level, pos, false);
            }
            return gone;
        });
        for (BlockPos pos : bound) {
            TurntableSignals.hold(level, pos, red);
        }
        return dropped;
    }

    public void release(Level level) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos pos : bound) {
            TurntableSignals.hold(level, pos, false);
        }
    }

    public void write(CompoundTag tag) {
        if (!bound.isEmpty()) {
            tag.putLongArray(KEY, bound.stream().mapToLong(BlockPos::asLong).toArray());
        }
    }

    public void read(CompoundTag tag) {
        bound.clear();
        for (long packed : tag.getLongArray(KEY)) {
            bound.add(BlockPos.of(packed));
        }
    }
}
