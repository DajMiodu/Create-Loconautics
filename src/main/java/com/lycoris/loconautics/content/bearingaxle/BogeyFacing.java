package com.lycoris.loconautics.content.bearingaxle;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class BogeyFacing {

    private BogeyFacing() {
    }

    @Nullable
    public static Direction.Axis axisOf(BlockState state) {
        if (!(state.getBlock() instanceof AbstractBogeyBlock) || !state.hasProperty(AbstractBogeyBlock.AXIS)) {
            return null;
        }
        return state.getValue(AbstractBogeyBlock.AXIS);
    }

    // creates bogey carries an axis and nothing else, so front is a convention the positive step of that axis
    @Nullable
    public static Direction defaultFront(BlockState state) {
        Direction.Axis axis = axisOf(state);
        if (axis == null) {
            return null;
        }
        return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
    }

    @Nullable
    public static Direction front(BlockState state, @Nullable Direction override) {
        Direction.Axis axis = axisOf(state);
        if (axis == null) {
            return null;
        }
        if (override != null && override.getAxis() == axis) {
            return override;
        }
        return defaultFront(state);
    }

    public static boolean onAxis(BlockState state, Direction dir) {
        Direction.Axis axis = axisOf(state);
        return axis != null && dir.getAxis() == axis;
    }
}
