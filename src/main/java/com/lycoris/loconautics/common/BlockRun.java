package com.lycoris.loconautics.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class BlockRun {

    private static final int RUN_SCAN_MAX = 256;

    private BlockRun() {}

    public static BlockPos anchor(BlockGetter level, BlockPos pos, TagKey<Block> tag, int max) {
        if (!level.getBlockState(pos).is(tag)) return pos.immutable();
        return anchor(pos, at -> level.getBlockState(at).is(tag), max);
    }

    public static BlockPos anchor(BlockPos pos, Predicate<BlockPos> member, int max) {
        if (!member.test(pos)) return pos.immutable();
        return anchor(pos, member, dominantAxis(pos, member, max), max);
    }

    public static BlockPos anchor(BlockPos pos, Predicate<BlockPos> member, Direction.Axis axis, int max) {
        if (!member.test(pos)) return pos.immutable();

        Direction back = Direction.get(Direction.AxisDirection.NEGATIVE, axis);
        int steps = 0;
        for (BlockPos at = pos.relative(back);
             steps < RUN_SCAN_MAX && member.test(at);
             at = at.relative(back)) {
            steps++;
        }
        return pos.relative(back, steps % max).immutable();
    }

    public static Direction.Axis dominantAxis(BlockGetter level, BlockPos pos, TagKey<Block> tag, int max) {
        return dominantAxis(pos, at -> level.getBlockState(at).is(tag), max);
    }

    public static Direction.Axis dominantAxis(BlockPos pos, Predicate<BlockPos> member, int max) {
        int alongX = reach(pos, Direction.WEST, member, max) + reach(pos, Direction.EAST, member, max);
        int alongZ = reach(pos, Direction.NORTH, member, max) + reach(pos, Direction.SOUTH, member, max);
        return alongZ > alongX ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static List<BlockPos> line(BlockGetter level, BlockPos pos, TagKey<Block> tag, int max) {
        return line(pos, at -> level.getBlockState(at).is(tag), max);
    }

    public static List<BlockPos> line(BlockPos pos, Predicate<BlockPos> member, int max) {
        if (!member.test(pos)) return List.of();

        int scanLimit = max + 1;
        Direction back = Direction.get(Direction.AxisDirection.NEGATIVE, dominantAxis(pos, member, max));
        Direction forward = back.getOpposite();

        List<BlockPos> found = new ArrayList<>();
        BlockPos start = pos.immutable();
        for (int i = 0; i < scanLimit && member.test(start.relative(back)); i++) {
            start = start.relative(back);
        }
        for (BlockPos at = start; member.test(at) && found.size() < scanLimit; at = at.relative(forward)) {
            found.add(at.immutable());
        }
        return found;
    }

    public static Direction axisSide(Direction.Axis axis, boolean positive) {
        return Direction.get(positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis);
    }

    private static int reach(BlockPos pos, Direction dir, Predicate<BlockPos> member, int max) {
        int count = 0;
        for (BlockPos at = pos.relative(dir); count <= max && member.test(at); at = at.relative(dir)) {
            count++;
        }
        return count;
    }
}
