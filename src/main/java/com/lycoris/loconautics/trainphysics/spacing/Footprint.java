package com.lycoris.loconautics.trainphysics.spacing;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public record Footprint(SableTrain train, ServerLevel level, double x, double y, double z, double ax, double az,
                        double halfLength, double halfWidth, double height) {

    private static final double UNKNOWN_OVERHANG = 3.0;
    private static final double UNKNOWN_HALF_WIDTH = 1.5;
    private static final double UNKNOWN_HEIGHT = 4.0;

    // a wide car must not block the train on the next track
    static final double WIDTH_CLIP = 1.4;
    static final double VERTICAL_REACH = 2.0;

    public static Footprint of(SableTrain train) {
        ServerLevel level = train.level();
        SableTrain.Car car = train.car();
        if (level == null || car == null || car.subLevelId() == null) {
            return null;
        }
        boolean railed = !train.isDerailed() && !train.isPhysics();
        if (!railed) {
            ServerSubLevel sub = SubLevelData.resolve(level, car.subLevelId());
            // a derailed car with no loaded body sits wherever it fell, its rail point is stale and blocks trains for nothing
            return sub == null ? null : ofBody(train, level, sub);
        }
        return ofRails(train, level, car);
    }

    private static Footprint ofRails(SableTrain train, ServerLevel level, SableTrain.Car car) {
        if (car.carriage() == null) {
            return null;
        }
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (bogey.rail() == null) {
                return null;
            }
        }
        Vector3d[] ref = TrainRailAdvance.bogeyReference(null, car);
        Vector3d mid;
        double dx;
        double dz;
        if (ref != null) {
            mid = ref[0];
            dx = ref[1].x;
            dz = ref[1].z;
        } else {
            mid = car.carriage().center();
            Vec3 forward = car.carriage().forward();
            dx = forward.x;
            dz = forward.z;
        }
        if (mid == null) {
            return null;
        }
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.0e-6) {
            dx = 1.0;
            dz = 0.0;
            flat = 1.0;
        }
        double spacing = Math.max(1.0, car.carriage().bogeySpacing());
        CarExtent extent = train.extent();
        double halfLength = extent == null ? spacing * 0.5 + UNKNOWN_OVERHANG
                : Math.max(spacing * 0.5, extent.halfLength());
        double halfWidth = extent == null ? UNKNOWN_HALF_WIDTH : extent.halfWidth();
        double height = extent == null ? UNKNOWN_HEIGHT : extent.height();
        return new Footprint(train, level, mid.x, mid.y, mid.z, dx / flat, dz / flat, halfLength, halfWidth, height);
    }

    private static Footprint ofBody(SableTrain train, ServerLevel level, ServerSubLevel sub) {
        var box = sub.getPlot().getBoundingBox();
        double spanX = box.maxX() + 1.0 - box.minX();
        double spanZ = box.maxZ() + 1.0 - box.minZ();
        boolean alongX = spanX >= spanZ;
        Vector3d center = SubLevelData.worldOf(sub, new Vector3d((box.minX() + box.maxX() + 1.0) * 0.5,
                (box.minY() + box.maxY() + 1.0) * 0.5, (box.minZ() + box.maxZ() + 1.0) * 0.5));
        Vector3d axis = SubLevelData.directionOf(sub, new Vector3d(alongX ? 1.0 : 0.0, 0.0, alongX ? 0.0 : 1.0));
        double flat = Math.sqrt(axis.x * axis.x + axis.z * axis.z);
        if (flat < 1.0e-6) {
            return null;
        }
        return new Footprint(train, level, center.x, center.y, center.z, axis.x / flat, axis.z / flat,
                Math.max(spanX, spanZ) * 0.5, Math.min(spanX, spanZ) * 0.5, Math.max(1.0, box.maxY() + 1.0 - box.minY()));
    }

    public double horizontalDistance(double px, double pz) {
        return Math.hypot(px - x, pz - z);
    }

    public double reach() {
        return Math.hypot(halfLength, Math.min(halfWidth, WIDTH_CLIP));
    }

    public boolean covers(double px, double py, double pz, double widen) {
        double dx = px - x;
        double dz = pz - z;
        double along = dx * ax + dz * az;
        double across = -dx * az + dz * ax;
        return Math.abs(along) <= halfLength + widen
                && Math.abs(across) <= Math.min(halfWidth, WIDTH_CLIP) + 0.5
                && Math.abs(py - y) <= VERTICAL_REACH;
    }

    public static double overlapDepth(Footprint a, Footprint b) {
        if (Math.abs(a.y - b.y) >= VERTICAL_REACH + Math.min(a.height, b.height) * 0.25) {
            return 0.0;
        }
        double gap = a.horizontalDistance(b.x, b.z);
        if (gap > a.reach() + b.reach()) {
            return 0.0;
        }
        double wa = Math.min(a.halfWidth, WIDTH_CLIP);
        double wb = Math.min(b.halfWidth, WIDTH_CLIP);
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double depth = Double.MAX_VALUE;
        depth = Math.min(depth, axisDepth(a.ax, a.az, dx, dz, a, wa, b, wb));
        depth = Math.min(depth, axisDepth(-a.az, a.ax, dx, dz, a, wa, b, wb));
        depth = Math.min(depth, axisDepth(b.ax, b.az, dx, dz, a, wa, b, wb));
        depth = Math.min(depth, axisDepth(-b.az, b.ax, dx, dz, a, wa, b, wb));
        return depth;
    }

    private static double axisDepth(double nx, double nz, double dx, double dz, Footprint a, double wa,
                                    Footprint b, double wb) {
        return radiusOn(a, wa, nx, nz) + radiusOn(b, wb, nx, nz) - Math.abs(dx * nx + dz * nz);
    }

    private static double radiusOn(Footprint f, double halfWidth, double nx, double nz) {
        return f.halfLength * Math.abs(f.ax * nx + f.az * nz) + halfWidth * Math.abs(-f.az * nx + f.ax * nz);
    }
}
