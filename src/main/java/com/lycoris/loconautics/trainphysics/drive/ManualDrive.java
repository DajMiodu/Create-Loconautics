package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ManualDrive {
    static final double TWO_DRIVER_ACCELERATION = 1.5;
    static final double THREE_DRIVER_ACCELERATION = 2.0;


    public static final double COUNTER_BRAKE = 2.0;

    private static final double REVERSE_DIVISOR = 4.0;

    private static final double IDLE_TARGET = 1.0e-4;

    private static final Map<AnalogControllerBlockEntity, Boolean> CURVE_TRACE = new WeakHashMap<>();

    record Driver(AnalogControllerBlockEntity controller, ServerSubLevel sub) {}

    record Pull(double target, int ahead, int back) {
        int pushers() {
            return Math.abs(ahead - back);
        }
    }

    private ManualDrive() {
    }

    static List<Driver> driversIn(ServerLevel level, List<UUID> members) {
        List<Driver> drivers = new ArrayList<>();
        for (UUID id : members) {
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            if (sub == null || sub.isRemoved()) {
                continue;
            }
            for (AnalogControllerBlockEntity controller
                    : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
                if ((controller.hasUser() || controller.isLocked()) && !controller.isScheduleControlled()
                        && LocomotiveHardware.hasTransmissionFor(sub, controller)) {
                    drivers.add(new Driver(controller, sub));
                }
            }
        }
        return drivers;
    }

    // a tug of war between cabs, the side with more drivers wins and an even split goes nowhere
    static Pull pull(List<Driver> drivers, Map<UUID, SableTrain> cars, Map<UUID, Integer> orientation,
                     boolean engineTurning, double gearedTop, double rpmTop) {
        int ahead = 0;
        int back = 0;
        double aheadTarget = 0.0;
        double backTarget = 0.0;
        for (Driver driver : drivers) {
            UUID id = driver.sub().getUniqueId();
            SableTrain car = cars.get(id);
            if (car == null) {
                continue;
            }
            double target = seedTarget(driver, car, orientation.getOrDefault(id, 1), engineTurning, gearedTop, rpmTop);
            if (target > IDLE_TARGET) {
                ahead++;
                aheadTarget = Math.max(aheadTarget, target);
            } else if (target < -IDLE_TARGET) {
                back++;
                backTarget = Math.min(backTarget, target);
            }
        }
        if (ahead == back) {
            return new Pull(0.0, ahead, back);
        }
        return new Pull(ahead > back ? aheadTarget : backTarget, ahead, back);
    }

    static double accelerationScale(int pushers) {
        if (pushers >= 3) {
            return THREE_DRIVER_ACCELERATION;
        }
        return pushers == 2 ? TWO_DRIVER_ACCELERATION : 1.0;
    }

    static double seedTarget(Driver driver, SableTrain car, int flip, boolean engineTurning, double gearedTop,
                             double rpmTop) {
        AnalogControllerBlockEntity controller = driver.controller();
        int input = controller.heldInput();
        boolean onCurve = car.car().carriage() != null && car.car().carriage().isOnCurve();
        if (controller.isLocked()) {
            // the setpoint is not recaptured on a curve, or the curve cap would become the new cruise speed
            if (Double.isNaN(controller.cruiseSpeed()) || (input != 0 && !onCurve)) {
                controller.setCruiseSpeed(car.speed());
            }
            Boolean wasOnCurve = CURVE_TRACE.put(controller, onCurve);
            if (wasOnCurve == null || wasOnCurve != onCurve) {
                LoconauticsConstants.LOGGER.info("[cruisecurve] onCurve {} input {} speed {} cruise {}", onCurve, input,
                        String.format("%.4f", car.speed()), String.format("%.4f", controller.cruiseSpeed()));
            }
            if (input == 0) {
                double cruise = controller.cruiseSpeed() * flip;
                return onCurve ? Math.copySign(Math.min(Math.abs(cruise), curveTop(gearedTop)), cruise) : cruise;
            }
        }
        Vec3 railForward = ConsistPower.forwardOf(car);
        if (input == 0 || !engineTurning || railForward == null) {
            return 0.0;
        }
        Direction ahead = controller.getBlockState().getValue(AnalogControllerBlock.FACING).getOpposite();
        Vector3d heading = new Vector3d(ahead.getStepX(), 0.0, ahead.getStepZ());
        driver.sub().logicalPose().transformNormal(heading, heading);
        double along = heading.x * railForward.x + heading.z * railForward.z;
        if (Math.abs(along) < 1.0e-6) {
            return 0.0;
        }
        double top = onCurve ? Math.min(rpmTop, curveTop(gearedTop)) : rpmTop;
        int direction = input * (along < 0.0 ? -1 : 1) * flip;
        // the slow direction from create, a hand driven train backs up at a quarter of its top speed
        return input < 0 ? top / REVERSE_DIVISOR * direction : top * direction;
    }

    // creates turning top speed, scaled up with the gearing the same way the straight top speed is
    static double curveTop(double gearedTop) {
        double baseline = TrainPropulsion.bearingAxleCapBpt();
        return AllConfigs.server().trains.trainTurningTopSpeed.getF() / 20.0
                * (baseline > 0.0 ? Math.max(1.0, gearedTop / baseline) : 1.0);
    }
}
