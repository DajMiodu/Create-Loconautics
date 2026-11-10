package com.lycoris.loconautics.content.analogcontroller;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class WhistleHonks {

    private static final Map<BlockPos, Integer> TICKS = new HashMap<>();

    private WhistleHonks() {
    }

    public static void honk(BlockPos whistle, boolean honk) {
        int ticks = TICKS.getOrDefault(whistle, 0);
        if (honk) {
            ticks = ticks == 0 ? 20 : 13;
        } else {
            ticks = ticks > 5 ? 6 : 0;
        }
        if (ticks == 0) {
            TICKS.remove(whistle);
            return;
        }
        TICKS.put(whistle.immutable(), ticks);
    }

    public static boolean isHonking(BlockPos whistle) {
        return TICKS.containsKey(whistle);
    }

    public static void tick() {
        TICKS.replaceAll((pos, ticks) -> ticks - 1);
        TICKS.values().removeIf(ticks -> ticks <= 0);
    }

    public static void clear() {
        TICKS.clear();
    }
}
