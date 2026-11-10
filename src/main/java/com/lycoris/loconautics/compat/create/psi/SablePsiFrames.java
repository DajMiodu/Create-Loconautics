package com.lycoris.loconautics.compat.create.psi;

import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class SablePsiFrames {

    private SablePsiFrames() {
    }

    public static float reachOf(double centerGap) {
        return (float) Math.max(0.0, Math.min(2.0, centerGap - 1.0));
    }

    public static Vec3 toWorld(@Nullable SubLevel sub, BlockPos pos, boolean render) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (sub == null) {
            return center;
        }
        Vector3d in = new Vector3d(center.x, center.y, center.z);
        Vector3d world = render && sub instanceof ClientSubLevel client
                ? client.renderPose().transformPosition(in, new Vector3d())
                : sub.logicalPose().transformPosition(in, new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    public static Vec3 dirToWorld(@Nullable SubLevel sub, Direction local, boolean render) {
        if (sub == null) {
            return Vec3.atLowerCornerOf(local.getNormal());
        }
        Vector3d in = new Vector3d(local.getStepX(), local.getStepY(), local.getStepZ());
        Vector3d dir = render && sub instanceof ClientSubLevel client
                ? client.renderPose().transformNormal(in, new Vector3d())
                : sub.logicalPose().transformNormal(in, new Vector3d());
        return new Vec3(dir.x, dir.y, dir.z);
    }

    @Nullable
    public static Float liveReach(PortableStorageInterfaceBlockEntity be, BlockPos partnerPos) {
        Level level = be.getLevel();
        if (level == null) {
            return null;
        }
        SubLevel own = Sable.HELPER.getContaining(be);
        SubLevel far = Sable.HELPER.getContaining(level, partnerPos);
        Vec3 axis = dirToWorld(own, be.getBlockState().getValue(PortableStorageInterfaceBlock.FACING), true);
        Vec3 delta = toWorld(far, partnerPos, true).subtract(toWorld(own, be.getBlockPos(), true));
        return reachOf(delta.dot(axis));
    }
}
