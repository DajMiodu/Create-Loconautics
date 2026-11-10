package com.lycoris.loconautics.common.strand;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

public final class StrandCollision {

    private StrandCollision() {
    }

    public static boolean pushOut(Level level, Vector3d point, double radius) {
        boolean moved = pushOutOfBlocks(level, point, radius);
        BoundingBox3d bounds = new BoundingBox3d(
                point.x - radius, point.y - radius, point.z - radius,
                point.x + radius, point.y + radius, point.z + radius);
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, bounds)) {
            Vector3d local = sub.logicalPose().transformPositionInverse(point, new Vector3d());
            if (pushOutOfBlocks(sub.getLevel(), local, radius)) {
                sub.logicalPose().transformPosition(local, point);
                moved = true;
            }
        }
        return moved;
    }

    private static boolean pushOutOfBlocks(Level level, Vector3d point, double radius) {
        AABB probe = new AABB(point.x - radius, point.y - radius, point.z - radius,
                point.x + radius, point.y + radius, point.z + radius);
        boolean moved = false;
        for (VoxelShape shape : level.getBlockCollisions(null, probe)) {
            for (AABB box : shape.toAabbs()) {
                moved |= pushOutOfBox(point, box.inflate(radius));
            }
        }
        return moved;
    }

    private static boolean pushOutOfBox(Vector3d p, AABB box) {
        if (p.x <= box.minX || p.x >= box.maxX || p.y <= box.minY || p.y >= box.maxY
                || p.z <= box.minZ || p.z >= box.maxZ) {
            return false;
        }
        double[] depth = {
                p.x - box.minX, box.maxX - p.x,
                p.y - box.minY, box.maxY - p.y,
                p.z - box.minZ, box.maxZ - p.z};
        int best = 0;
        for (int i = 1; i < depth.length; i++) {
            if (depth[i] < depth[best]) {
                best = i;
            }
        }
        switch (best) {
            case 0 -> p.x = box.minX;
            case 1 -> p.x = box.maxX;
            case 2 -> p.y = box.minY;
            case 3 -> p.y = box.maxY;
            case 4 -> p.z = box.minZ;
            default -> p.z = box.maxZ;
        }
        return true;
    }
}
