package com.lycoris.loconautics.content.controls.panel;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class PanelDriveBus {

    private record Target(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class Bid {
        long tick;
        float current;
        float previous;
    }

    private static final Map<Target, Bid> BIDS = new HashMap<>();

    public static float report(Level level, BlockPos pos, float value) {
        Target target = new Target(level.dimension(), pos);
        long now = level.getGameTime();
        Bid bid = BIDS.computeIfAbsent(target, key -> new Bid());

        if (bid.tick != now) {
            bid.previous = bid.current;
            bid.current = 0f;
            bid.tick = now;
        }
        bid.current = Math.max(bid.current, value);

        return Math.max(bid.current, bid.previous);
    }

    public static void release(Level level, BlockPos pos) {
        BIDS.remove(new Target(level.dimension(), pos));
    }

    public static void clear() {
        BIDS.clear();
    }

    private PanelDriveBus() {
    }
}
