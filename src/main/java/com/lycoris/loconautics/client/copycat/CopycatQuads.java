package com.lycoris.loconautics.client.copycat;

import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

public final class CopycatQuads {

    public static final int ALL_AXES = -1;

    private CopycatQuads() {}

    public static BlockState materialOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item) return item.getBlock().defaultBlockState();
        return Blocks.COAL_BLOCK.defaultBlockState();
    }

    public static BakedModel donorModel(BlockState material) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
    }

    public static void crop(List<BakedQuad> out, BlockState material, RandomSource rand,
                            RenderType renderType, AABB box) {
        BakedModel donor = donorModel(material);
        cropQuads(out, donor.getQuads(material, null, rand, ModelData.EMPTY, renderType), box, ALL_AXES);
        for (Direction side : Direction.values()) {
            cropQuads(out, donor.getQuads(material, side, rand, ModelData.EMPTY, renderType), box, ALL_AXES);
        }
    }

    public static void cropQuads(List<BakedQuad> out, List<BakedQuad> template, AABB box, int depthAxis) {
        for (AABB piece : split(box)) {
            AABB source = corner(piece, depthAxis);
            Vec3 move = new Vec3(piece.minX - source.minX, piece.minY - source.minY, piece.minZ - source.minZ);
            for (BakedQuad quad : template) {
                out.add(BakedQuadHelper.cloneWithCustomGeometry(quad,
                        BakedModelHelper.cropAndMove(quad.getVertices(), quad.getSprite(), source, move)));
            }
        }
    }

    private static AABB corner(AABB piece, int depthAxis) {
        double[] min = {piece.minX, piece.minY, piece.minZ};
        double[] max = {piece.maxX, piece.maxY, piece.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (depthAxis != ALL_AXES && depthAxis != axis) continue;
            double lo = min[axis];
            double hi = max[axis];
            boolean outer = depthAxis == axis && (lo <= 0.0 || hi >= 1.0);
            boolean takeHigh = depthAxis == ALL_AXES || outer ? lo + hi >= 1.0 : lo + hi < 1.0;
            min[axis] = takeHigh ? 1.0 - (hi - lo) : 0.0;
            max[axis] = takeHigh ? 1.0 : hi - lo;
        }
        return new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    private static List<AABB> split(AABB box) {
        List<AABB> pieces = new ArrayList<>(8);
        for (double[] x : spans(box.minX, box.maxX)) {
            for (double[] y : spans(box.minY, box.maxY)) {
                for (double[] z : spans(box.minZ, box.maxZ)) {
                    pieces.add(new AABB(x[0], y[0], z[0], x[1], y[1], z[1]));
                }
            }
        }
        return pieces;
    }

    private static List<double[]> spans(double min, double max) {
        if (min < 0.5 && max > 0.5 && (min > 0.0 || max < 1.0)) {
            return List.of(new double[] {min, 0.5}, new double[] {0.5, max});
        }
        return List.of(new double[] {min, max});
    }
}
