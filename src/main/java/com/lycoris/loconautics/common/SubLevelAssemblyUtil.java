package com.lycoris.loconautics.common;

import com.lycoris.loconautics.LoconauticsConstants;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.util.Set;

public final class SubLevelAssemblyUtil {

    private SubLevelAssemblyUtil() {}

    // every assembly goes through here, calling assembleblocks directly bites
    @Nullable
    public static ServerSubLevel assemble(ServerLevel level, BlockPos anchor, Iterable<BlockPos> blocks,
                                          BoundingBox3ic bounds) {
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
        if (sub != null) {
            anchorRotationPoint(sub);
        }
        return sub;
    }

    public static void anchorRotationPoint(ServerSubLevel sub) {
        Vector3dc rotationPoint = sub.logicalPose().rotationPoint();
        if (rotationPoint.x() != 0.0 || rotationPoint.y() != 0.0 || rotationPoint.z() != 0.0) {
            return;
        }
        Vector3dc centerOfMass = sub.getMassTracker().getCenterOfMass();
        if (centerOfMass != null) {
            sub.logicalPose().rotationPoint().set(centerOfMass);
        }
    }

    @Nullable
    public static ServerSubLevel assembleSingleBlock(ServerLevel level, BlockPos pos, String tag) {
        try {
            BoundingBox3i bounds = new BoundingBox3i(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
            ServerSubLevel sub = assemble(level, pos, Set.of(pos.immutable()), bounds);
            if (sub == null) {
                LoconauticsConstants.LOGGER.warn("{} block at {} failed to assemble into a sub-level", tag, pos);
            }
            return sub;
        } catch (Exception e) {
            LoconauticsConstants.LOGGER.error("{} assembly at {} threw", tag, pos, e);
            return null;
        }
    }
}
