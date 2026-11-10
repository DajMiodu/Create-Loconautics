package com.lycoris.loconautics.content.hose.rope;

import com.lycoris.loconautics.common.strand.RopeManager;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class HoseRopeManager extends RopeManager<HoseRope> {

    private static final WorldAttached<HoseRopeManager> ATTACHED =
            new WorldAttached<>(l -> l instanceof ServerLevel ? new HoseRopeManager() : null);

    private HoseRopeManager() {}

    @Nullable
    public static HoseRopeManager get(Level level) {
        return ATTACHED.get(level);
    }

    public static void onPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        tick(system, timeStep, get(system.getLevel()));
    }
}