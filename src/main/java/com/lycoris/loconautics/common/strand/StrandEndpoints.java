package com.lycoris.loconautics.common.strand;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

public final class StrandEndpoints {

    private StrandEndpoints() {
    }

    @Nullable
    public static ServerSubLevel subLevel(@Nullable SubLevelContainer container, @Nullable UUID subId) {
        if (subId == null || container == null) {
            return null;
        }
        return container.getSubLevel(subId) instanceof ServerSubLevel sub ? sub : null;
    }

    public static boolean isLoaded(ServerLevel level, @Nullable SubLevelContainer container,
                                   @Nullable UUID subId, BlockPos local) {
        if (subId != null) {
            return subLevel(container, subId) != null;
        }
        return level.isLoaded(local);
    }

    public static boolean drivenBy(ServerLevel level, @Nullable UUID subA, @Nullable UUID subB) {
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(candidate);
            if (container != null && (subLevel(container, subA) != null || subLevel(container, subB) != null)) {
                return candidate == level;
            }
        }
        return level == level.getServer().overworld();
    }

    @Nullable
    public static BlockEntity blockEntity(ServerLevel level, @Nullable SubLevelContainer container,
                                          @Nullable UUID subId, BlockPos local) {
        if (subId == null) {
            return level.getBlockEntity(local);
        }
        ServerSubLevel sub = subLevel(container, subId);
        return sub == null ? null : sub.getLevel().getBlockEntity(local);
    }

    @Nullable
    public static Vector3d worldPoint(ServerLevel level, @Nullable SubLevelContainer container,
                                      @Nullable UUID subId, BlockPos local) {
        ServerSubLevel sub = subLevel(container, subId);
        if (subId != null && sub == null) {
            return null;
        }
        BlockEntity be = (sub != null ? sub.getLevel() : level).getBlockEntity(local);
        Vec3 point = be instanceof StrandHolder holder ? holder.getAttachmentPoint() : Vec3.atCenterOf(local);
        Vector3d anchor = new Vector3d(point.x, point.y, point.z);
        return sub == null ? anchor : sub.logicalPose().transformPosition(anchor, new Vector3d());
    }
}
