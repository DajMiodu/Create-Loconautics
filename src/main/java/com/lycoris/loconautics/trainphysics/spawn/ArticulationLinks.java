package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.mixin.sable.swivel.SwivelBearingAccessor;
import com.lycoris.loconautics.mixin.sable.swivel.SwivelPlateAccessor;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.coupling.BlockEntityCouplingProvider;
import com.lycoris.loconautics.trainphysics.coupling.TwistLink;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import dev.simulated_team.simulated.index.SimBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArticulationLinks {

    private static final int SETTLE_TICKS = 40;

    public record Hinge(BlockPos bearing, BlockPos front, Direction facing,
                        Set<BlockPos> bearingSide, Set<BlockPos> plateSide) {
    }

    public record BuiltCar(SableTrain.Car car, BlockPos anchor) {
    }

    private ArticulationLinks() {
    }

    public static void registerProviders() {
        TrainConsist.registerProvider(new BlockEntityCouplingProvider<>(
                SwivelBearingBlockEntity.class,
                be -> trainCar(be.getSubLevelID()),
                SwivelBearingBlockEntity::isAssembled).twist(ArticulationLinks::twistLink));
        TrainConsist.registerProvider(new BlockEntityCouplingProvider<>(
                SwivelBearingPlateBlockEntity.class,
                be -> trainCar(((SwivelPlateAccessor) (Object) be).loconautics$getParentSubLevelId()),
                be -> true));
    }

    @Nullable
    static TwistLink twistLink(ServerSubLevel own, SwivelBearingBlockEntity bearing) {
        UUID plateSub = trainCar(bearing.getSubLevelID());
        BlockPos platePos = bearing.getPlatePos();
        if (plateSub == null || platePos == null) {
            return null;
        }
        Direction facing = bearing.getBlockState().getValue(SwivelBearingBlock.FACING);
        Vec3 hingeA = bearing.getBlockPos().relative(facing).getCenter();
        Vec3 hingeB = platePos.relative(facing).getCenter();
        return new TwistLink(own.getUniqueId(), new Vector3d(hingeA.x, hingeA.y, hingeA.z), plateSub,
                new Vector3d(hingeB.x, hingeB.y, hingeB.z), TwistLink.Kind.SWIVEL, 1.0,
                new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()));
    }

    private static UUID trainCar(UUID subLevelId) {
        return subLevelId != null && SableTrainRegistry.bySubLevel(subLevelId) != null ? subLevelId : null;
    }

    static boolean isHinge(BlockState state, BlockState nextState, Direction offset) {
        return facesAcross(state, offset) || facesAcross(nextState, offset.getOpposite());
    }

    private static boolean facesAcross(BlockState state, Direction toward) {
        return state.getBlock() instanceof SwivelBearingBlock
                && !state.getValue(SwivelBearingBlock.ASSEMBLED)
                && state.getValue(SwivelBearingBlock.FACING) == toward;
    }

    static void absorbBogeylessFronts(ServerLevel level, List<Set<BlockPos>> carriages, Set<BlockPos> claimed,
                                      AABB scan, List<AABB> honeyGlue) {
        for (Set<BlockPos> cluster : carriages) {
            Deque<BlockPos> bearings = new ArrayDeque<>(bearingsIn(level, cluster));
            while (!bearings.isEmpty()) {
                BlockPos bearing = bearings.poll();
                BlockPos front = bearing.relative(level.getBlockState(bearing).getValue(SwivelBearingBlock.FACING));
                if (claimed.contains(front) || !scan.contains(Vec3.atCenterOf(front))) {
                    continue;
                }
                BlockState frontState = level.getBlockState(front);
                if (frontState.isAir() || frontState.getBlock() instanceof ITrackBlock
                        || !BlockMovementChecks.isMovementNecessary(frontState, level, front)) {
                    continue;
                }
                Set<BlockPos> extra = StationAssembly.floodFillCarriage(level, front, scan, claimed, honeyGlue);
                claimed.addAll(extra);
                cluster.addAll(extra);
                bearings.addAll(bearingsIn(level, extra));
            }
        }
    }

    private static List<BlockPos> bearingsIn(ServerLevel level, Set<BlockPos> cluster) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SwivelBearingBlock && !state.getValue(SwivelBearingBlock.ASSEMBLED)) {
                out.add(pos);
            }
        }
        return out;
    }

    static List<Hinge> collect(ServerLevel level, List<Set<BlockPos>> carriages) {
        List<Hinge> out = new ArrayList<>();
        for (Set<BlockPos> cluster : carriages) {
            for (BlockPos bearing : bearingsIn(level, cluster)) {
                Direction facing = level.getBlockState(bearing).getValue(SwivelBearingBlock.FACING);
                BlockPos front = bearing.relative(facing);
                Set<BlockPos> other = StationAssembly.carriageContaining(carriages, front);
                if (other != null && other != cluster) {
                    out.add(new Hinge(bearing, front, facing, cluster, other));
                }
            }
        }
        return out;
    }

    private record Relink(ServerLevel level, UUID bearingSub, UUID plateSub, BlockPos bearingPlot, long dueTick) {
    }

    private static final List<Relink> RELINKS = new ArrayList<>();

    private record Tracked(ServerLevel level, UUID bearingSub, UUID plateSub, BlockPos bearingPlot) {
    }

    private static final List<Tracked> TRACKED = new ArrayList<>();

    static void linkAll(ServerLevel level, List<Hinge> hinges, Map<Set<BlockPos>, BuiltCar> built) {
        for (Hinge hinge : hinges) {
            BuiltCar bearingCar = built.get(hinge.bearingSide());
            BuiltCar plateCar = built.get(hinge.plateSide());
            if (bearingCar == null || plateCar == null) {
                continue;
            }
            BlockPos bearingPlot = link(level, hinge, bearingCar, plateCar);
            if (bearingPlot != null) {
                TRACKED.add(new Tracked(level, bearingCar.car().subLevelId(), plateCar.car().subLevelId(), bearingPlot));
                RELINKS.add(new Relink(level, bearingCar.car().subLevelId(), plateCar.car().subLevelId(),
                        bearingPlot, level.getServer().getTickCount() + SETTLE_TICKS));
            }
        }
    }

    public static void processPending(MinecraftServer server) {
        diagnose(server);
        if (RELINKS.isEmpty()) {
            return;
        }
        Iterator<Relink> it = RELINKS.iterator();
        while (it.hasNext()) {
            Relink next = it.next();
            if (server.getTickCount() < next.dueTick()) {
                continue;
            }
            it.remove();
            ServerSubLevel plateSub = SubLevelData.resolve(next.level(), next.plateSub());
            boolean bothAlive = SableTrainRegistry.bySubLevel(next.bearingSub()) != null
                    && SableTrainRegistry.bySubLevel(next.plateSub()) != null && plateSub != null;
            if (bothAlive && next.level().getBlockEntity(next.bearingPlot()) instanceof SwivelBearingBlockEntity bearing
                    && bearing.isAssembled()) {
                bearing.reattachConstraint(plateSub, true);
            }
        }
    }

    private static double yawOf(ServerSubLevel sub) {
        Vector3d forward = sub.logicalPose().orientation().transform(new Vector3d(0.0, 0.0, 1.0));
        return Math.toDegrees(Math.atan2(forward.x, forward.z));
    }

    private static void diagnose(MinecraftServer server) {
        if (TRACKED.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        Iterator<Tracked> it = TRACKED.iterator();
        while (it.hasNext()) {
            Tracked hinge = it.next();
            ServerSubLevel subA = SubLevelData.resolve(hinge.level(), hinge.bearingSub());
            ServerSubLevel subB = SubLevelData.resolve(hinge.level(), hinge.plateSub());
            if (subA == null || subB == null
                    || !(hinge.level().getBlockEntity(hinge.bearingPlot()) instanceof SwivelBearingBlockEntity bearing)
                    || !bearing.isAssembled() || bearing.getPlatePos() == null) {
                it.remove();
                continue;
            }
            Direction facing = bearing.getBlockState().getValue(SwivelBearingBlock.FACING);
            Vec3 worldA = subA.logicalPose().transformPosition(hinge.bearingPlot().relative(facing).getCenter());
            Vec3 worldB = subB.logicalPose().transformPosition(bearing.getPlatePos().relative(facing).getCenter());
            double gap = worldA.distanceTo(worldB);
            double relYaw = Mth.wrapDegrees(yawOf(subA) - yawOf(subB));
            boolean moving = Math.abs(relYaw) > 1.0 || gap > 0.1;
            if (!moving && server.getTickCount() % 100 != 0) {
                continue;
            }
            RotaryConstraintHandle handle = ((SwivelBearingAccessor) (Object) bearing).loconautics$getHandle();
            SableTrain trainA = SableTrainRegistry.bySubLevel(hinge.bearingSub());
            SableTrain trainB = SableTrainRegistry.bySubLevel(hinge.plateSub());
        }
    }

    private static BlockPos link(ServerLevel level, Hinge hinge, BuiltCar bearingCar, BuiltCar plateCar) {
        ServerSubLevel bearingSub = SubLevelData.resolve(level, bearingCar.car().subLevelId());
        ServerSubLevel plateSub = SubLevelData.resolve(level, plateCar.car().subLevelId());
        if (bearingSub == null || plateSub == null) {
            return null;
        }
        BlockPos bearingPlot = inPlot(bearingSub, bearingCar.anchor(), hinge.bearing());
        BlockPos platePlot = inPlot(plateSub, plateCar.anchor(), hinge.bearing());
        if (!(level.getBlockEntity(bearingPlot) instanceof SwivelBearingBlockEntity)) {
            LoconauticsConstants.LOGGER.warn("[articulation] no swivel bearing at {} in its car, hinge skipped",
                    bearingPlot);
            return null;
        }
        if (!level.getBlockState(platePlot).isAir()) {
            LoconauticsConstants.LOGGER.warn("[articulation] plate cell {} is taken, hinge skipped", platePlot);
            return null;
        }
        BlockState plateState = SimBlocks.SWIVEL_BEARING_LINK_BLOCK.getDefaultState()
                .setValue(SwivelBearingPlateBlock.FACING, hinge.facing());
        level.setBlockAndUpdate(platePlot, plateState);
        if (!(level.getBlockEntity(platePlot) instanceof SwivelBearingPlateBlockEntity)) {
            LoconauticsConstants.LOGGER.warn("[articulation] plate did not land at {} in car {}, hinge skipped",
                    platePlot, plateSub.getUniqueId());
            return null;
        }
        level.setBlockAndUpdate(bearingPlot,
                level.getBlockState(bearingPlot).setValue(SwivelBearingBlock.ASSEMBLED, true));
        if (!(level.getBlockEntity(bearingPlot) instanceof SwivelBearingBlockEntity bearing)) {
            return null;
        }
        bearing.setSubLevelID(plateSub.getUniqueId());
        bearing.setPlatePos(platePlot);
        ScrollValueBehaviour locking = bearing.getBehaviour(ScrollValueBehaviour.TYPE);
        if (locking != null) {
            locking.setValue(SwivelBearingBlockEntity.LockingSetting.UNLOCKED_ALWAYS.ordinal());
        }
        bearing.reattachConstraint(plateSub, true);
        bearing.sendData();
        return bearingPlot;
    }

    private static BlockPos inPlot(ServerSubLevel sub, BlockPos anchor, BlockPos worldPos) {
        return worldPos.subtract(anchor).offset(sub.getPlot().getCenterBlock());
    }

    public static boolean hinged(ServerLevel level, UUID a, UUID b) {
        return linkedPartner(level, a, b) || linkedPartner(level, b, a);
    }

    private static boolean linkedPartner(ServerLevel level, UUID bearingCar, UUID plateCar) {
        ServerSubLevel sub = SubLevelData.resolve(level, bearingCar);
        if (sub == null) {
            return false;
        }
        for (SwivelBearingBlockEntity bearing : SubLevelBlocks.findAll(sub, SwivelBearingBlockEntity.class)) {
            if (bearing.isAssembled() && plateCar.equals(bearing.getSubLevelID())) {
                return true;
            }
        }
        return false;
    }

    public static void suspend(ServerLevel level, List<SableTrain> cars) {
        for (SableTrain car : cars) {
            ServerSubLevel sub = SubLevelData.resolve(level, car.car().subLevelId());
            if (sub == null) {
                continue;
            }
            for (SwivelBearingBlockEntity bearing : SubLevelBlocks.findAll(sub, SwivelBearingBlockEntity.class)) {
                if (bearing.isAssembled() && trainCar(bearing.getSubLevelID()) != null) {
                    ((SwivelBearingAccessor) (Object) bearing).loconautics$removeHandle();
                }
            }
        }
    }

    public static void resume(ServerLevel level, List<SableTrain> cars) {
        for (SableTrain car : cars) {
            ServerSubLevel sub = SubLevelData.resolve(level, car.car().subLevelId());
            if (sub == null) {
                continue;
            }
            for (SwivelBearingBlockEntity bearing : SubLevelBlocks.findAll(sub, SwivelBearingBlockEntity.class)) {
                if (bearing.isAssembled() && trainCar(bearing.getSubLevelID()) != null
                        && SubLevelData.resolve(level, bearing.getSubLevelID()) != null) {
                    bearing.reattachConstraint(SubLevelData.resolve(level, bearing.getSubLevelID()), true);
                }
            }
        }
    }

    public static void release(ServerLevel level, ServerSubLevel sub) {
        for (SwivelBearingBlockEntity bearing : SubLevelBlocks.findAll(sub, SwivelBearingBlockEntity.class)) {
            unlink(level, bearing);
        }
        for (SwivelBearingPlateBlockEntity plate : SubLevelBlocks.findAll(sub, SwivelBearingPlateBlockEntity.class)) {
            BlockPos parent = ((SwivelPlateAccessor) (Object) plate).loconautics$getParent();
            if (parent != null && level.getBlockEntity(parent) instanceof SwivelBearingBlockEntity bearing) {
                unlink(level, bearing);
            }
        }
    }

    private static void unlink(ServerLevel level, SwivelBearingBlockEntity bearing) {
        if (!bearing.isAssembled() || trainCar(bearing.getSubLevelID()) == null) {
            return;
        }
        SwivelBearingAccessor access = (SwivelBearingAccessor) (Object) bearing;
        access.loconautics$removeHandle();
        access.loconautics$destroyPlate();
        BlockPos pos = bearing.getBlockPos();
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(SwivelBearingBlock.ASSEMBLED, false));
        bearing.setSubLevelID(null);
        bearing.setPlatePos(null);
        bearing.sendData();
    }
}
