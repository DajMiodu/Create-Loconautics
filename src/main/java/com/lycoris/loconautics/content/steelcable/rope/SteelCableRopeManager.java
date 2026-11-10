package com.lycoris.loconautics.content.steelcable.rope;

import com.lycoris.loconautics.common.strand.RopeManager;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class SteelCableRopeManager extends RopeManager<SteelCableRope> {

    private static final WorldAttached<SteelCableRopeManager> ATTACHED =
            new WorldAttached<>(l -> l instanceof ServerLevel ? new SteelCableRopeManager() : null);

    private SteelCableRopeManager() {}

    @Nullable
    public static SteelCableRopeManager get(Level level) {
        return ATTACHED.get(level);
    }

    public static void onPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        tick(system, timeStep, get(system.getLevel()));
    }
}