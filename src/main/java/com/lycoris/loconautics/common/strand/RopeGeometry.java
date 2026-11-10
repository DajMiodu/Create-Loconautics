package com.lycoris.loconautics.common.strand;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public final class RopeGeometry {

    private RopeGeometry() {}

    public static List<Vector3d> tautPolyline(Vector3dc a, Vector3dc b, double restLength) {
        return polyline(a, b, restLength);
    }

    public static int linkCount(double restLength) {
        return Math.max(1, (int) Math.ceil(restLength - 1.0e-3)) + 1;
    }

    @Nullable
    public static List<Vec3> interpolateRopePoints(@Nullable float[] curr, @Nullable float[] prev, BlockPos base, float partialTicks) {
        if (curr == null || curr.length < 6) return null;
        float[] interp = (prev != null && prev.length == curr.length) ? prev : curr;
        List<Vec3> out = new ArrayList<>(curr.length / 3);
        for (int i = 0; i + 2 < curr.length; i += 3) {
            float x = interp[i]     + (curr[i]     - interp[i])     * partialTicks;
            float y = interp[i + 1] + (curr[i + 1] - interp[i + 1]) * partialTicks;
            float z = interp[i + 2] + (curr[i + 2] - interp[i + 2]) * partialTicks;
            out.add(new Vec3(base.getX() + (double) x, base.getY() + (double) y, base.getZ() + (double) z));
        }
        return out;
    }

    public static AABB renderBounds(@Nullable float[] pts, BlockPos base, double inflate) {
        if (pts == null || pts.length < 6) {
            return new AABB(base);
        }
        // seeded at the whole anchor cell so the block the rope hangs off is always inside the box
        double minX = 0, minY = 0, minZ = 0, maxX = 1, maxY = 1, maxZ = 1;
        for (int i = 0; i + 2 < pts.length; i += 3) {
            minX = Math.min(minX, pts[i]);     maxX = Math.max(maxX, pts[i]);
            minY = Math.min(minY, pts[i + 1]); maxY = Math.max(maxY, pts[i + 1]);
            minZ = Math.min(minZ, pts[i + 2]); maxZ = Math.max(maxZ, pts[i + 2]);
        }
        return new AABB(
                base.getX() + minX, base.getY() + minY, base.getZ() + minZ,
                base.getX() + maxX, base.getY() + maxY, base.getZ() + maxZ).inflate(inflate);
    }

    public static List<Vec3> redistributeEvenly(List<Vec3> points) {
        int n = points.size() - 1;
        if (n <= 0) {
            return points;
        }
        double[] segLen = new double[n];
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            segLen[i] = points.get(i).distanceTo(points.get(i + 1));
            total += segLen[i];
        }
        if (total < 1.0e-6) {
            return points;
        }
        double target = total / n;
        List<Vec3> out = new ArrayList<>(points.size());
        out.add(points.get(0));
        double segStart = 0.0;
        int seg = 0;
        for (int k = 1; k <= n; k++) {
            double want = k * target;
            while (seg < n - 1 && segStart + segLen[seg] < want) {
                segStart += segLen[seg];
                seg++;
            }
            double into = segLen[seg] < 1.0e-9 ? 0.0 : Mth.clamp((want - segStart) / segLen[seg], 0.0, 1.0);
            out.add(points.get(seg).lerp(points.get(seg + 1), into));
        }
        return out;
    }

    private static List<Vector3d> polyline(Vector3dc a, Vector3dc b, double restLength) {
        int n = linkCount(restLength);
        Vector3d dir = new Vector3d(b).sub(a);
        double span = dir.length();
        if (span > 1.0e-6) {
            dir.div(span);
        } else {
            dir.set(0.0, -1.0, 0.0);
        }
        List<Vector3d> points = new ArrayList<>(n + 1);
        Vector3d cursor = new Vector3d(a);
        points.add(new Vector3d(cursor));
        for (int i = 0; i < n; i++) {
            cursor.add(dir);
            points.add(new Vector3d(cursor));
        }
        return points;
    }
}