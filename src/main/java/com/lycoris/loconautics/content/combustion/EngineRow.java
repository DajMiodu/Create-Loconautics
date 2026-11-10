package com.lycoris.loconautics.content.combustion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class EngineRow {

    private static final int SCAN_LIMIT = SmallEngineTuning.MAX_TANDEM + 2;

    private EngineRow() {}

    public static boolean isEngine(BlockGetter level, BlockPos pos, Direction facing) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SmallEngineBlock && state.getValue(SmallEngineBlock.HORIZONTAL_FACING) == facing;
    }

    public static boolean joinsToward(BlockState state, Direction facing, Direction dir) {
        EnginePart part = state.getValue(SmallEngineBlock.PART);
        boolean both = part == EnginePart.MIDDLE;
        return dir == facing ? both || part == EnginePart.BACK : both || part == EnginePart.FRONT;
    }

    public static EnginePart partOf(boolean joinAhead, boolean joinBehind) {
        return joinAhead ? (joinBehind ? EnginePart.MIDDLE : EnginePart.BACK)
                : (joinBehind ? EnginePart.FRONT : EnginePart.SINGLE);
    }

    public static boolean linked(BlockGetter level, BlockPos pos, BlockState self, Direction facing, Direction dir) {
        BlockPos next = pos.relative(dir);
        return isEngine(level, next, facing)
                && joinsToward(self, facing, dir)
                && joinsToward(level.getBlockState(next), facing, dir.getOpposite());
    }

    public static boolean linked(BlockGetter level, BlockPos pos, Direction facing, Direction dir) {
        return linked(level, pos, level.getBlockState(pos), facing, dir);
    }

    public static int lengthAround(BlockGetter level, BlockPos pos, Direction facing) {
        return 1 + reach(level, pos, facing, facing) + reach(level, pos, facing.getOpposite(), facing);
    }

    public static BlockPos headOf(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos head = pos;
        for (int i = 0; i < SCAN_LIMIT && linked(level, head, facing, facing); i++) {
            head = head.relative(facing);
        }
        return head;
    }

    @Nullable
    public static BlockPos survivorNear(BlockGetter level, BlockPos gone) {
        for (int distance = 1; distance < SmallEngineTuning.MAX_TANDEM; distance++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = gone.relative(side, distance);
                if (!(level.getBlockState(candidate).getBlock() instanceof SmallEngineBlock)) continue;
                Direction facing = level.getBlockState(candidate).getValue(SmallEngineBlock.HORIZONTAL_FACING);
                if (facing.getAxis() == side.getAxis() && isEngine(level, candidate, facing)) return candidate;
            }
        }
        return null;
    }

    public static BlockPos tailOf(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos tail = pos;
        for (int i = 0; i < SCAN_LIMIT && linked(level, tail, facing, facing.getOpposite()); i++) {
            tail = tail.relative(facing.getOpposite());
        }
        return tail;
    }

    public static List<BlockPos> membersFrom(BlockGetter level, BlockPos head, Direction facing) {
        List<BlockPos> members = new ArrayList<>(SmallEngineTuning.MAX_TANDEM);
        BlockPos at = head;
        for (int i = 0; i < SmallEngineTuning.MAX_TANDEM && isEngine(level, at, facing); i++) {
            members.add(at);
            if (!linked(level, at, facing, facing.getOpposite())) break;
            at = at.relative(facing.getOpposite());
        }
        return members;
    }

    public static int freeEndLength(BlockGetter level, BlockPos pos, Direction facing, Direction towardUs) {
        if (!isEngine(level, pos, facing) || joinsToward(level.getBlockState(pos), facing, towardUs)) return 0;
        return 1 + reach(level, pos, towardUs.getOpposite(), facing);
    }

    private static int reach(BlockGetter level, BlockPos pos, Direction dir, Direction facing) {
        int steps = 0;
        BlockPos at = pos;
        while (steps < SCAN_LIMIT && linked(level, at, facing, dir)) {
            steps++;
            at = at.relative(dir);
        }
        return steps;
    }
}
