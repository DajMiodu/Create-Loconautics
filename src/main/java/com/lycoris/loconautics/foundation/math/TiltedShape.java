package com.lycoris.loconautics.foundation.math;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.UnaryOperator;

public final class TiltedShape extends VoxelShape {

    private static final double NORMAL_STEP = 0.01;

    public static VoxelShape of(VoxelShape straight, List<AABB> tiltedBoxes,
                                UnaryOperator<Vec3> toBlock, UnaryOperator<Vec3> toTilted) {
        return of(straight, tiltedBoxes, toBlock, toTilted, null);
    }

    public static VoxelShape of(VoxelShape straight, List<AABB> tiltedBoxes,
                                UnaryOperator<Vec3> toBlock, UnaryOperator<Vec3> toTilted,
                                @Nullable List<Vec3[]> outline) {
        if (tiltedBoxes.isEmpty()) return straight;
        return new TiltedShape(straight, List.copyOf(tiltedBoxes), toBlock, toTilted, outline);
    }

    private final VoxelShape straight;

    private final List<AABB> tilted;

    private final UnaryOperator<Vec3> toBlock;

    private final UnaryOperator<Vec3> toTilted;

    private final AABB enclosing;

    @Nullable
    private final List<Vec3[]> outline;

    private TiltedShape(VoxelShape straight, List<AABB> tilted,
                        UnaryOperator<Vec3> toBlock, UnaryOperator<Vec3> toTilted,
                        @Nullable List<Vec3[]> outline) {
        super(fullVoxel());
        this.straight = straight;
        this.tilted = tilted;
        this.toBlock = toBlock;
        this.toTilted = toTilted;
        this.outline = outline == null ? null : List.copyOf(outline);
        this.enclosing = enclose(straight, tilted, toBlock);
    }

    private static DiscreteVoxelShape fullVoxel() {
        BitSetDiscreteVoxelShape voxel = new BitSetDiscreteVoxelShape(1, 1, 1);
        voxel.fill(0, 0, 0);
        return voxel;
    }

    private static AABB enclose(VoxelShape straight, List<AABB> tilted, UnaryOperator<Vec3> toBlock) {
        AABB bounds = straight.isEmpty() ? null : straight.bounds();
        for (AABB box : tilted) {
            for (Vec3 corner : corners(box)) {
                Vec3 point = toBlock.apply(corner);
                AABB grown = new AABB(point, point);
                bounds = bounds == null ? grown : bounds.minmax(grown);
            }
        }
        return bounds == null ? new AABB(0, 0, 0, 1, 1, 1) : bounds;
    }

    private static Vec3[] corners(AABB box) {
        return new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ),
        };
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return switch (axis) {
            case X -> DoubleArrayList.wrap(new double[]{enclosing.minX, enclosing.maxX});
            case Y -> DoubleArrayList.wrap(new double[]{enclosing.minY, enclosing.maxY});
            case Z -> DoubleArrayList.wrap(new double[]{enclosing.minZ, enclosing.maxZ});
        };
    }

    @Override
    @Nullable
    public BlockHitResult clip(Vec3 from, Vec3 to, BlockPos pos) {
        BlockHitResult flat = straight.clip(from, to, pos);
        BlockHitResult slope = clipTilted(from, to, pos);
        if (flat == null || slope == null) return flat == null ? slope : flat;
        return from.distanceToSqr(slope.getLocation()) < from.distanceToSqr(flat.getLocation())
                ? slope : flat;
    }

    @Nullable
    private BlockHitResult clipTilted(Vec3 from, Vec3 to, BlockPos pos) {
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        BlockHitResult hit = AABB.clip(tilted, toTilted.apply(from.subtract(origin)),
                toTilted.apply(to.subtract(origin)), BlockPos.ZERO);
        if (hit == null) return null;

        Vec3 point = toBlock.apply(hit.getLocation());
        Vec3 stepped = toBlock.apply(hit.getLocation()
                .add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(NORMAL_STEP)));
        Vec3 normal = stepped.subtract(point);
        return new BlockHitResult(point.add(origin),
                Direction.getNearest(normal.x, normal.y, normal.z), pos, false);
    }

    @Override
    public void forAllEdges(Shapes.DoubleLineConsumer action) {
        if (outline != null) {
            for (Vec3[] segment : outline) {
                action.consume(segment[0].x, segment[0].y, segment[0].z,
                        segment[1].x, segment[1].y, segment[1].z);
            }
            return;
        }
        straight.forAllEdges(action);
        for (AABB box : tilted) {
            Vec3[] corners = corners(box);
            for (int i = 0; i < corners.length; i++) {
                corners[i] = toBlock.apply(corners[i]);
            }
            edge(action, corners, 0, 1);
            edge(action, corners, 0, 2);
            edge(action, corners, 1, 3);
            edge(action, corners, 2, 3);
            edge(action, corners, 4, 5);
            edge(action, corners, 4, 6);
            edge(action, corners, 5, 7);
            edge(action, corners, 6, 7);
            edge(action, corners, 0, 4);
            edge(action, corners, 1, 5);
            edge(action, corners, 2, 6);
            edge(action, corners, 3, 7);
        }
    }

    private static void edge(Shapes.DoubleLineConsumer action, Vec3[] corners, int from, int to) {
        Vec3 a = corners[from];
        Vec3 b = corners[to];
        action.consume(a.x, a.y, a.z, b.x, b.y, b.z);
    }
}
