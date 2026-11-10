package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TracklessCars {

    private TracklessCars() {
    }

    @Nullable
    public static SableTrain.Car build(ServerSubLevel sub) {
        List<BlockPos> positions = SubLevelSplitWatcher.scanBogeyPlotPositions(sub.getPlot());
        if (positions.isEmpty()) {
            return null;
        }
        Vector3d axis = localAxis(sub.getLevel().getBlockState(positions.get(0)));
        positions.sort(Comparator.comparingDouble(p -> p.getX() * axis.x + p.getZ() * axis.z));
        BlockPos center = sub.getPlot().getCenterBlock();
        List<SableTrain.Bogey> bogeys = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            bogeys.add(new SableTrain.Bogey(pos.subtract(center), null));
        }
        BlockPos first = positions.get(0);
        BlockPos last = positions.get(positions.size() - 1);
        Vector3d trail = new Vector3d(first.getX() + 0.5, first.getY(), first.getZ() + 0.5);
        Vector3d lead = new Vector3d(last.getX() + 0.5, last.getY(), last.getZ() + 0.5);
        if (positions.size() == 1) {
            trail.sub(new Vector3d(axis).mul(0.5));
            lead.add(new Vector3d(axis).mul(0.5));
        }
        return new SableTrain.Car(sub.getUniqueId(), null, bogeys, lead, trail);
    }

    public static Vec3 railDirection(ServerSubLevel sub, BlockPos bogeyPlotPos) {
        Vector3d local = localAxis(sub.getLevel().getBlockState(bogeyPlotPos));
        return SubLevelData.toVec3(SubLevelData.directionOf(sub, local));
    }

    private static Vector3d localAxis(BlockState bogeyState) {
        boolean alongX = !bogeyState.hasProperty(AbstractBogeyBlock.AXIS)
                || bogeyState.getValue(AbstractBogeyBlock.AXIS) == Direction.Axis.X;
        return alongX ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1);
    }
}
