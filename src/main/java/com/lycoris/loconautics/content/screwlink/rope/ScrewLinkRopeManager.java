package com.lycoris.loconautics.content.screwlink.rope;

import com.lycoris.loconautics.common.strand.RopeManager;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ScrewLinkRopeManager extends RopeManager<ScrewLinkRope> {

    private static final WorldAttached<ScrewLinkRopeManager> ATTACHED =
            new WorldAttached<>(l -> l instanceof ServerLevel ? new ScrewLinkRopeManager() : null);

    private ScrewLinkRopeManager() {}

    @Nullable
    public static ScrewLinkRopeManager get(Level level) {
        return ATTACHED.get(level);
    }

    @Override
    public void remove(SubLevelPhysicsSystem system, UUID id) {
        ScrewLinkRope rope = get(id);
        if (rope != null && rope.getHook() != null && rope.getHook().isActive()) {
            system.removeObject(rope.getHook());
        }
        super.remove(system, id);
    }

    public static void onPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        tick(system, timeStep, get(system.getLevel()));
    }
}
