package com.lycoris.loconautics.client;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ConductorHats {

    private ConductorHats() {}

    private static final Map<UUID, Set<BlockPos>> DRIVERS = new HashMap<>();

    public static void setDriver(UUID subLevelId, BlockPos pos, boolean driving) {
        if (driving) {
            DRIVERS.computeIfAbsent(subLevelId, id -> new HashSet<>()).add(pos.immutable());
            return;
        }
        Set<BlockPos> inLevel = DRIVERS.get(subLevelId);
        if (inLevel != null && inLevel.remove(pos) && inLevel.isEmpty()) {
            DRIVERS.remove(subLevelId);
        }
    }

    public static boolean wearsHat(BlockEntity be) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        return sub != null && driving(sub.getUniqueId(), be.getBlockPos());
    }

    // client outside the plot and never syncs back to false, so oring it in leaves the hat on forever
    public static boolean hatOverride(BlockEntity be, boolean original) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        if (sub == null || !SableTrainClientRegistry.isTrain(sub.getUniqueId())) {
            return original;
        }
        return driving(sub.getUniqueId(), be.getBlockPos());
    }

    public static PartialModel hatFor(BlockEntity be, PartialModel original) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        if (sub == null || !SableTrainClientRegistry.isTrain(sub.getUniqueId())) {
            return original;
        }
        return LoconauticsPartialModels.conductorsHat(be.getBlockPos().asLong());
    }

    private static boolean driving(UUID subLevelId, BlockPos pos) {
        Set<BlockPos> inLevel = DRIVERS.get(subLevelId);
        return inLevel != null && inLevel.contains(pos);
    }
}
