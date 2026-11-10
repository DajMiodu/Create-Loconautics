package com.lycoris.loconautics.trainphysics.fx;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class SableTrainCollisionParticles {

    private static final double MARGIN = 0.75;

    private SableTrainCollisionParticles() {
    }

    public static boolean withinTrain(ServerLevel level, double x, double y, double z) {
        if (SableTrainRegistry.isEmpty()) {
            return false;
        }
        SubLevelContainer sideless = SubLevelContainer.getContainer(level);
        if (!(sideless instanceof ServerSubLevelContainer container)) {
            return false;
        }
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() != level) {
                continue;
            }
            UUID id = train.car().subLevelId();
            if (id == null
                    || !(container.getSubLevel(id) instanceof ServerSubLevel sub) || sub.isRemoved()) {
                continue;
            }
            BoundingBox3dc b = sub.boundingBox();
            if (b == null) {
                continue;
            }
            if (x >= b.minX() - MARGIN && x <= b.maxX() + MARGIN
                    && y >= b.minY() - MARGIN && y <= b.maxY() + MARGIN
                    && z >= b.minZ() - MARGIN && z <= b.maxZ() + MARGIN) {
                return true;
            }
        }
        return false;
    }
}
