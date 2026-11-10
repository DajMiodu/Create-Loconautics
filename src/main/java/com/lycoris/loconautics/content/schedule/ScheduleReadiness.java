package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class ScheduleReadiness {

    private ScheduleReadiness() {
    }

    public record Result(boolean ok, String reasonKey) {
        static final Result PASS = new Result(true, null);

        static Result fail(String key) {
            return new Result(false, key);
        }
    }

    public static Result check(ServerSubLevel sub, AnalogControllerBlockEntity controller, UUID trainId) {
        if (sub == null || sub.isRemoved()) {
            return Result.fail("no_sub_level");
        }
        if (sub.getPlot().getLoadedChunks().isEmpty()) {
            return Result.fail("not_loaded");
        }
        if (controller == null) {
            return Result.fail("no_controller");
        }
        BlockPos seat = driverSeat(sub, trainId);
        if (seat == null || !atControls(controller, seat)) {
            return Result.fail("no_driver");
        }

        BearingAxleBlockEntity axle = BearingAxleValidity.resolve(sub);
        if (axle == null) {
            return Result.fail("invalid_bearing_axle");
        }
        List<TransmissionBlockEntity> transmissions = SubLevelBlocks.findAll(sub, TransmissionBlockEntity.class);
        if (transmissions.isEmpty()) {
            return Result.fail("no_transmission");
        }
        TransmissionBlockEntity transmission = null;
        for (TransmissionBlockEntity candidate : transmissions) {
            if (LocomotiveHardware.link(candidate, axle) == LocomotiveHardware.Link.OK) {
                transmission = candidate;
                break;
            }
        }
        if (transmission == null) {
            return Result.fail(LocomotiveHardware.linkKey(LocomotiveHardware.link(transmissions.get(0), axle)));
        }

        LocomotiveHardware.Result wiring = LocomotiveHardware.validate(controller, transmission, axle);
        if (!wiring.ok()) {
            return Result.fail(wiring.messageKey());
        }
        if (Math.abs(transmission.getSpeed()) < 1.0e-3f) {
            return Result.fail("no_rotation");
        }
        return Result.PASS;
    }

    @Nullable
    public static BlockPos driverSeat(ServerSubLevel sub, UUID trainId) {
        for (BlazeBurnerBlockEntity burner : SubLevelBlocks.findAll(sub, BlazeBurnerBlockEntity.class)) {
            if (burner.hat && burner.getHeatLevelFromBlock() != BlazeBurnerBlock.HeatLevel.NONE) {
                return burner.getBlockPos();
            }
        }
        var bounds = sub.getPlot().getBoundingBox();
        AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
        for (LivingEntity mob : sub.getLevel().getEntitiesOfClass(LivingEntity.class, box)) {
            if (mob.getVehicle() != null && ScheduleConductor.drives(mob, trainId)) {
                return BlockPos.containing(mob.getVehicle().position());
            }
        }
        return null;
    }

    public static boolean atControls(AnalogControllerBlockEntity controller, BlockPos driver) {
        return atControls(controller.getBlockState(), controller.getBlockPos(), driver);
    }

    private static boolean atControls(BlockState controller, BlockPos controllerPos, BlockPos driver) {
        BlockPos front = controllerPos.relative(controller.getValue(HorizontalDirectionalBlock.FACING));
        return driver.getX() == front.getX() && driver.getZ() == front.getZ()
                && (driver.getY() == front.getY() || driver.getY() == front.getY() - 1);
    }

    public static boolean seatedAtControls(LivingEntity rider) {
        if (!(rider.getVehicle() instanceof SeatEntity seat)) {
            return false;
        }
        BlockPos driver = BlockPos.containing(seat.position());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int up = 0; up <= 1; up++) {
                BlockPos at = driver.relative(side).above(up);
                BlockState state = rider.level().getBlockState(at);
                if (state.getBlock() instanceof AnalogControllerBlock && atControls(state, at, driver)) {
                    return true;
                }
            }
        }
        return false;
    }

    public record Reach(boolean forward, boolean backward) {
        static final Reach FORWARD = new Reach(true, false);
    }

    public static Reach reach(@Nullable ServerSubLevel sub, UUID trainId, @Nullable Direction axleFront) {
        AnalogControllerBlockEntity driven = drivenController(sub, trainId);
        if (driven == null || axleFront == null) {
            return Reach.FORWARD;
        }
        boolean forward = looksAlong(driven, axleFront);
        boolean backward = looksAlong(driven, axleFront.getOpposite());
        for (AnalogControllerBlockEntity other : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
            if (other == driven || !LocomotiveHardware.validate(sub, other).ok()) {
                continue;
            }
            forward |= looksAlong(other, axleFront);
            backward |= looksAlong(other, axleFront.getOpposite());
        }
        return forward || backward ? new Reach(forward, backward) : Reach.FORWARD;
    }

    public static boolean looksAlong(AnalogControllerBlockEntity controller, Direction direction) {
        return controller.getBlockState().getValue(HorizontalDirectionalBlock.FACING).getOpposite() == direction;
    }

    @Nullable
    public static AnalogControllerBlockEntity controllerAt(ServerSubLevel sub, BlockPos driver) {
        for (AnalogControllerBlockEntity controller : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
            if (atControls(controller, driver)) {
                return controller;
            }
        }
        return null;
    }

    @Nullable
    public static AnalogControllerBlockEntity drivenController(@Nullable ServerSubLevel sub, UUID trainId) {
        if (sub == null || sub.isRemoved()) {
            return null;
        }
        BlockPos seat = driverSeat(sub, trainId);
        return seat == null ? null : controllerAt(sub, seat);
    }
}
