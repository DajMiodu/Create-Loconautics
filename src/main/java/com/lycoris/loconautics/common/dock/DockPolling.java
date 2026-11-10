package com.lycoris.loconautics.common.dock;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DockPolling {

    private DockPolling() {
    }

    public static <T> void maybeUndock(boolean docked, @Nullable T partner, Predicate<T> stillDocked,
                                       Runnable onUndocked) {
        if (docked && (partner == null || !stillDocked.test(partner))) {
            onUndocked.run();
        }
    }

    public static <T> void poll(IDockable host, Iterable<T> candidates, Predicate<T> eligible,
                                Function<T, SubLevel> subLevelOf, BiFunction<T, SubLevel, Vec3> worldCenterOf,
                                BiConsumer<T, SubLevel> onSeated) {
        if (!host.canDock()) {
            return;
        }
        Vec3 dock = host.getDockPoint();
        double r = host.dockRadius();
        for (T candidate : candidates) {
            if (!eligible.test(candidate)) {
                continue;
            }
            SubLevel sub = subLevelOf.apply(candidate);
            if (sub == null) {
                continue;
            }
            if (worldCenterOf.apply(candidate, sub).distanceToSqr(dock) < r * r) {
                onSeated.accept(candidate, sub);
                return;
            }
        }
    }
}
