package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BearingAxleValidity {

    public enum Status {
        VALID, NO_BOGEY, BOGEY_TAKEN, WRONG_AXIS
    }

    private static final Direction[] BOGEY_DIRS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private record Classified(long tick, List<BearingAxleBlockEntity> valid) {
    }

    private static final Map<UUID, Classified> CACHE = new HashMap<>();

    public static void clearCache() {
        CACHE.clear();
    }

    private BearingAxleValidity() {
    }

    @Nullable
    public static BlockPos driverBogey(BlockGetter level, BlockPos axlePos) {
        for (Direction dir : BOGEY_DIRS) {
            BlockPos pos = axlePos.relative(dir);
            if (level.getBlockState(pos).getBlock() instanceof AbstractBogeyBlock) {
                return pos.immutable();
            }
        }
        return null;
    }

    public static List<BearingAxleBlockEntity> valid(ServerSubLevel sub) {
        if (sub == null || sub.isRemoved()) {
            return List.of();
        }
        long now = sub.getLevel().getGameTime();
        Classified cached = CACHE.get(sub.getUniqueId());
        if (cached != null && cached.tick() == now) {
            return cached.valid();
        }
        List<BearingAxleBlockEntity> valid =
                classify(sub.getLevel(), SubLevelBlocks.findAll(sub, BearingAxleBlockEntity.class));
        CACHE.put(sub.getUniqueId(), new Classified(now, valid));
        return valid;
    }

    public static List<BearingAxleBlockEntity> classify(BlockGetter level, List<BearingAxleBlockEntity> axles) {
        List<BearingAxleBlockEntity> valid = new ArrayList<>();
        for (BearingAxleBlockEntity axle : axles) {
            BlockPos bogey = driverBogey(level, axle.getBlockPos());
            if (bogey == null) {
                axle.setValidity(Status.NO_BOGEY, null);
                continue;
            }
            if (!BogeyFacing.onAxis(level.getBlockState(bogey), axleFrontLocal(axle))) {
                axle.setValidity(Status.WRONG_AXIS, bogey);
                continue;
            }
            axle.setValidity(Status.VALID, bogey);
            valid.add(axle);
        }
        return valid;
    }

    @Nullable
    public static BearingAxleBlockEntity resolve(ServerSubLevel sub) {
        return pick(valid(sub));
    }

    @Nullable
    public static BearingAxleBlockEntity resolve(BlockGetter level, List<BearingAxleBlockEntity> axles) {
        return pick(classify(level, axles));
    }

    @Nullable
    private static BearingAxleBlockEntity pick(List<BearingAxleBlockEntity> valid) {
        BearingAxleBlockEntity best = null;
        double bestStrength = -1.0;
        for (BearingAxleBlockEntity axle : valid) {
            double strength = Math.abs(axle.getSpeed());
            if (best == null || strength > bestStrength
                    || (strength == bestStrength && olderThan(axle, best))) {
                best = axle;
                bestStrength = strength;
            }
        }
        return best;
    }

    public static Direction axleFrontLocal(BearingAxleBlockEntity axle) {
        BlockState state = axle.getBlockState();
        if (!(state.getBlock() instanceof BearingAxleBlock)) {
            return Direction.NORTH;
        }
        return BearingAxleBlock.front(state);
    }

    private static boolean olderThan(BearingAxleBlockEntity a, BearingAxleBlockEntity b) {
        if (a.getPlacedTime() != b.getPlacedTime()) {
            return a.getPlacedTime() < b.getPlacedTime();
        }
        return a.getBlockPos().asLong() < b.getBlockPos().asLong();
    }
}
