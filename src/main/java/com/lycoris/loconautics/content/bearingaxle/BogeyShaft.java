package com.lycoris.loconautics.content.bearingaxle;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public final class BogeyShaft {

    public static final BooleanProperty SHAFTED = BooleanProperty.create("shafted");

    private BogeyShaft() {
    }

    public static boolean isShafted(BlockState state) {
        return state.hasProperty(SHAFTED) && state.getValue(SHAFTED);
    }

    public static boolean passes(BlockGetter level, BlockPos bogey, Direction along) {
        BlockState state = level.getBlockState(bogey);
        return isShafted(state) && state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == along.getAxis();
    }

    public static void relink(Level level, BlockPos bogey, Direction.Axis axis) {
        if (level.isClientSide) {
            return;
        }
        Direction plus = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        BlockPos a = bogey.relative(plus);
        BlockPos b = bogey.relative(plus.getOpposite());
        KineticBlockEntity ka = kinetic(level, a);
        KineticBlockEntity kb = kinetic(level, b);
        dropLink(ka, kb, b);
        dropLink(kb, ka, a);
        if (ka != null) {
            ka.updateSpeed = true;
        }
        if (kb != null) {
            kb.updateSpeed = true;
        }
    }

    @Nullable
    public static KineticBlockEntity endpoint(Level level, BlockPos bogey, Direction.Axis axis) {
        KineticBlockEntity idle = null;
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction side = Direction.get(sign, axis);
            BlockPos endPos = bogey.relative(side);
            KineticBlockEntity kbe = kinetic(level, endPos);
            if (kbe == null || !(kbe.getBlockState().getBlock() instanceof IRotate rotate)
                    || !rotate.hasShaftTowards(level, endPos, kbe.getBlockState(), side.getOpposite())) {
                continue;
            }
            if (kbe.getSpeed() != 0) {
                return kbe;
            }
            idle = kbe;
        }
        return idle;
    }

    @Nullable
    private static KineticBlockEntity kinetic(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity kbe ? kbe : null;
    }

    private static void dropLink(@Nullable KineticBlockEntity end, @Nullable KineticBlockEntity source, BlockPos sourcePos) {
        if (end != null && source != null && end.hasSource() && end.source.equals(sourcePos)) {
            source.detachKinetics();
        }
    }

    public static boolean staleLink(KineticBlockEntity be, BlockPos far, Level level) {
        if (be.hasSource() && be.source.equals(far)) {
            return true;
        }
        return level.getBlockEntity(far) instanceof KineticBlockEntity other
                && other.hasSource() && other.source.equals(be.getBlockPos());
    }
}
