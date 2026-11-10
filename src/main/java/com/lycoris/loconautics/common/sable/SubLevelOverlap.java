package com.lycoris.loconautics.common.sable;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public final class SubLevelOverlap {

    private SubLevelOverlap() {
    }

    public record Hit(Vector3dc axis, double depth) {}

    private static final double EPS = 1.0e-6;
    private static final double MIN_CROSS_LENGTH = 1.0e-4;

    public static boolean intersects(SubLevel a, SubLevel b) {
        return test(a, b) != null;
    }

    public static Hit test(SubLevel a, SubLevel b) {
        return test(a, b, null, null);
    }

    public static Hit test(SubLevel a, SubLevel b, Vector3dc shiftA, Vector3dc shiftB) {
        if (a == null || b == null || a == b) {
            return null;
        }
        Box boxA = Box.of(a, shiftA);
        Box boxB = Box.of(b, shiftB);
        if (boxA == null || boxB == null) {
            return null;
        }
        if (boxA.center().distance(boxB.center()) > boxA.radius() + boxB.radius()) {
            return null;
        }
        if (separatingAxis(boxA, boxB) == null) {
            return null;
        }
        return deepestBlockHit(a, b, boxA, boxB, shiftA, shiftB);
    }

    private static Hit deepestBlockHit(SubLevel a, SubLevel b, Box wholeA, Box wholeB, Vector3dc shiftA,
                                       Vector3dc shiftB) {
        List<Box> blocksA = blocksTouching(a, shiftA, wholeB);
        if (blocksA.isEmpty()) {
            return null;
        }
        List<Box> blocksB = blocksTouching(b, shiftB, wholeA);
        Hit deepest = null;
        for (Box blockA : blocksA) {
            for (Box blockB : blocksB) {
                Hit hit = separatingAxis(blockA, blockB);
                if (hit != null && (deepest == null || hit.depth() > deepest.depth())) {
                    deepest = hit;
                }
            }
        }
        return deepest;
    }

    private static List<Box> blocksTouching(SubLevel sub, Vector3dc shift, Box other) {
        List<Box> touching = new ArrayList<>();
        SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
            if (state.isAir()) {
                return true;
            }
            Box block = Box.ofBlock(sub, pos, shift);
            if (separatingAxis(block, other) != null) {
                touching.add(block);
            }
            return true;
        });
        return touching;
    }

    private static Hit separatingAxis(Box a, Box b) {
        double[][] r = new double[3][3];
        double[][] absR = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = a.axes()[i].dot(b.axes()[j]);
                absR[i][j] = Math.abs(r[i][j]) + EPS;
            }
        }
        Vector3d t = new Vector3d(b.center()).sub(a.center());
        double[] ta = {t.dot(a.axes()[0]), t.dot(a.axes()[1]), t.dot(a.axes()[2])};

        double best = Double.MAX_VALUE;
        Vector3d bestAxis = null;

        for (int i = 0; i < 3; i++) {
            double reach = b.half()[0] * absR[i][0] + b.half()[1] * absR[i][1] + b.half()[2] * absR[i][2];
            double overlap = a.half()[i] + reach - Math.abs(ta[i]);
            if (overlap <= 0.0) {
                return null;
            }
            if (overlap < best) {
                best = overlap;
                bestAxis = new Vector3d(a.axes()[i]).mul(ta[i] >= 0.0 ? 1.0 : -1.0);
            }
        }
        for (int j = 0; j < 3; j++) {
            double reach = a.half()[0] * absR[0][j] + a.half()[1] * absR[1][j] + a.half()[2] * absR[2][j];
            double along = ta[0] * r[0][j] + ta[1] * r[1][j] + ta[2] * r[2][j];
            double overlap = reach + b.half()[j] - Math.abs(along);
            if (overlap <= 0.0) {
                return null;
            }
            if (overlap < best) {
                best = overlap;
                bestAxis = new Vector3d(b.axes()[j]).mul(along >= 0.0 ? 1.0 : -1.0);
            }
        }
        for (int i = 0; i < 3; i++) {
            int i1 = (i + 1) % 3;
            int i2 = (i + 2) % 3;
            for (int j = 0; j < 3; j++) {
                int j1 = (j + 1) % 3;
                int j2 = (j + 2) % 3;
                double reachA = a.half()[i1] * absR[i2][j] + a.half()[i2] * absR[i1][j];
                double reachB = b.half()[j1] * absR[i][j2] + b.half()[j2] * absR[i][j1];
                double gap = Math.abs(ta[i2] * r[i1][j] - ta[i1] * r[i2][j]);
                if (reachA + reachB - gap <= 0.0) {
                    return null;
                }
                double length = Math.sqrt(Math.max(0.0, 1.0 - r[i][j] * r[i][j]));
                if (length < MIN_CROSS_LENGTH) {
                    continue;
                }
                double overlap = (reachA + reachB - gap) / length;
                if (overlap < best) {
                    best = overlap;
                    Vector3d axis = new Vector3d(a.axes()[i]).cross(b.axes()[j]).normalize();
                    bestAxis = axis.mul(t.dot(axis) >= 0.0 ? 1.0 : -1.0);
                }
            }
        }
        return bestAxis == null ? null : new Hit(bestAxis, best);
    }

    private record Box(Vector3d center, Vector3d[] axes, double[] half, double radius) {

        static Box ofBlock(SubLevel sub, BlockPos pos, Vector3dc shift) {
            Vector3d[] axes = {
                    SubLevelData.directionOf(sub, new Vector3d(1.0, 0.0, 0.0)),
                    SubLevelData.directionOf(sub, new Vector3d(0.0, 1.0, 0.0)),
                    SubLevelData.directionOf(sub, new Vector3d(0.0, 0.0, 1.0))
            };
            Vector3d center = SubLevelData.worldOf(sub, new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (shift != null) {
                center.add(shift);
            }
            return new Box(center, axes, new double[]{0.5, 0.5, 0.5}, Math.sqrt(0.75));
        }

        static Box of(SubLevel sub, Vector3dc shift) {
            BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
            double hx = (bounds.maxX() + 1 - bounds.minX()) * 0.5;
            double hy = (bounds.maxY() + 1 - bounds.minY()) * 0.5;
            double hz = (bounds.maxZ() + 1 - bounds.minZ()) * 0.5;
            if (hx <= 0.0 || hy <= 0.0 || hz <= 0.0) {
                return null;
            }
            Vector3d local = new Vector3d(bounds.minX() + hx, bounds.minY() + hy, bounds.minZ() + hz);
            Vector3d[] axes = {
                    SubLevelData.directionOf(sub, new Vector3d(1.0, 0.0, 0.0)),
                    SubLevelData.directionOf(sub, new Vector3d(0.0, 1.0, 0.0)),
                    SubLevelData.directionOf(sub, new Vector3d(0.0, 0.0, 1.0))
            };
            Vector3d center = SubLevelData.worldOf(sub, local);
            if (shift != null) {
                center.add(shift);
            }
            return new Box(center, axes, new double[]{hx, hy, hz},
                    Math.sqrt(hx * hx + hy * hy + hz * hz));
        }
    }
}
