package com.lycoris.loconautics.trainphysics.door;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.decoration.slidingDoor.DoorControlBehaviour;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class TrainDoors {

    private TrainDoors() {
    }

    public static void setDoors(SableTrain train, boolean open) {
        if (train.isGhost() || train.doorsOpen() == open) {
            return;
        }
        train.setDoorsOpen(open);
        ServerLevel level = train.level();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID start = train.car().subLevelId();
        if (container == null || start == null) {
            return;
        }
        // closing always closes every door, only opening is filtered, so a side the station never opens cant get stuck open
        DoorControl mode = open ? stationDoorControl(train) : DoorControl.ALL;
        for (UUID id : TrainConsist.connectedSubLevels(level, start)) {
            if (container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                setDoorsIn(sub, open, mode);
            }
        }
    }

    private static DoorControl stationDoorControl(SableTrain train) {
        GlobalStation station = train.atStation() != null ? train.atStation() : train.currentStation();
        if (station == null) {
            return DoorControl.ALL;
        }
        ServerLevel stationLevel = train.level().getServer().getLevel(station.getBlockEntityDimension());
        if (stationLevel == null || !stationLevel.isLoaded(station.getBlockEntityPos())) {
            return DoorControl.ALL;
        }
        DoorControlBehaviour behaviour = BlockEntityBehaviour.get(stationLevel, station.getBlockEntityPos(),
                DoorControlBehaviour.TYPE);
        return behaviour == null ? DoorControl.ALL : behaviour.mode;
    }

    private static void setDoorsIn(ServerSubLevel sub, boolean open, DoorControl mode) {
        ServerLevel subLevel = sub.getLevel();
        var bounds = sub.getPlot().getBoundingBox();
        Vec3 center = new Vec3((bounds.minX() + bounds.maxX() + 1) / 2.0,
                (bounds.minY() + bounds.maxY() + 1) / 2.0, (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
        SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
            if (isLowerHalf(state) && state.getBlock() instanceof DoorBlock door) {
                if (mode != DoorControl.ALL && !mode.matches(worldFacing(sub, pos, state, center))) {
                    return true;
                }
                door.setOpen(null, subLevel, state, pos.immutable(), open);
            }
            return true;
        });
    }

    private static Direction worldFacing(ServerSubLevel sub, BlockPos pos, BlockState state, Vec3 center) {
        Direction stateFacing = state.getValue(DoorBlock.FACING);
        Direction wallFacing = Direction.get(Direction.AxisDirection.POSITIVE, stateFacing.getAxis());
        Vec3 diff = Vec3.atCenterOf(pos)
                .add(Vec3.atLowerCornerOf(stateFacing.getNormal()).scale(-0.45))
                .subtract(center);
        if (wallFacing.getAxis().choose(diff.x, diff.y, diff.z) < 0) {
            wallFacing = wallFacing.getOpposite();
        }
        Vec3 world = sub.logicalPose().transformNormal(Vec3.atLowerCornerOf(wallFacing.getNormal()));
        return Direction.getNearest(world.x, world.y, world.z);
    }

    private static boolean isLowerHalf(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }
}
