package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class TrainPropulsion {

    public static final double MPS2_TO_BPT2 = 0.05 * 0.05;

    static double tractionAccelLimit(SableTrain train, double consistMass) {
        double massRatio = LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get() / Math.max(1.0, consistMass);
        double scaled = LoconauticsConfig.DYNAMICS_BASE_ACCELERATION.get()
                * Math.pow(massRatio, LoconauticsConfig.DYNAMICS_MASS_EXPONENT.get());
        double limit = Math.max(LoconauticsConfig.DYNAMICS_MIN_ACCELERATION.get(),
                Math.min(scaled, LoconauticsConfig.DYNAMICS_MAX_ACCELERATION.get()));
        double boosted = limit * ConsistPower.driverBoost(train);
        return isMonorail(train) ? boosted * MONORAIL_TRACTION_BOOST : boosted;
    }

    static final double MONORAIL_TRACTION_BOOST = 3.0;

    public static boolean isMonorail(SableTrain train) {
        return train.car().carriage() != null && train.car().carriage().isOnMonorail();
    }

    public static double brakeDecelLimit() {
        return LoconauticsConfig.DYNAMICS_BASE_DECELERATION.get();
    }

    static boolean isBraking(double target, double current) {
        return Math.abs(target) < Math.abs(current) || target * current < 0.0;
    }

    static double brakeLimit(SableTrain train, double target, double current) {
        boolean countering = ConsistPower.isManual(train) && target * current < 0.0;
        return brakeDecelLimit() * (countering ? ManualDrive.COUNTER_BRAKE : 1.0);
    }

    static double inclineDegrees(SableTrain train) {
        Vec3 forward = train.car().carriage().forward();
        double length = forward.length();
        if (length < 1.0e-9) {
            return 0.0;
        }
        return Math.toDegrees(Math.asin(Math.min(1.0, Math.abs(forward.y) / length)));
    }

    static boolean isClimbing(SableTrain train, double rpm) {
        Vec3 forward = train.car().carriage().forward();
        double travelDir = Math.abs(train.speed()) > 1.0e-4 ? Math.signum(train.speed()) : (rpm >= 0.0 ? 1.0 : -1.0);
        return travelDir * forward.y > 1.0e-4;
    }

    static final double SAFETY_MAX_SPEED_MPS = 200.0;

    public static double speedCeilingMps() {
        return AxleGearing.speedCeiling(LoconauticsConfig.TRAIN_MAX_SPEED.get(), SAFETY_MAX_SPEED_MPS,
                LoconauticsConfig.GEARING_CAP_AT_CREATE_TOP_SPEED.get(), createTopSpeedMps());
    }

    public static double gearingCeilingRatio() {
        return AxleGearing.ceilingRatio(bearingAxleCapMps(), speedCeilingMps());
    }

    public static double bodySpeedCapMps(SableTrain train) {
        return Math.min(speedCeilingMps(), Math.max(bearingAxleCapMps(), ConsistPower.peakTopBpt(train) * 20.0));
    }

    public static double gearedTopBpt(SableTrain train) {
        ConsistPower.Result power = ConsistPower.resultFor(train);
        return power == null ? bearingAxleCapBpt() : power.gearedTopBpt();
    }

    static void updateAxleRedstone(SableTrain train) {
        if (train.isDerailed() || train.isPhysics()) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return;
        }
        ServerLevel subLevel = sub.getLevel();
        for (BearingAxleBlockEntity axle
                : com.lycoris.loconautics.common.sable.SubLevelBlocks.findAll(sub, BearingAxleBlockEntity.class)) {
            BlockPos pos = axle.getBlockPos();
            BlockState state = subLevel.getBlockState(pos);
            if (!(state.getBlock() instanceof BearingAxleBlock)) {
                continue;
            }
            Direction plusFace  = BearingAxleBlock.getPlusFace(state);
            Direction minusFace = plusFace.getOpposite();
            axle.setRightRedstone(subLevel.getSignal(pos.relative(plusFace),  plusFace)  > 0);
            axle.setLeftRedstone(subLevel.getSignal(pos.relative(minusFace), minusFace) > 0);
            axle.setGearSignal(BearingAxleBlock.gearSignal(subLevel, pos, state));
        }
    }

    static final double STANDSTILL_SPEED = 1.0e-3;

    static void applyDynamics(SableTrain train) {
        if (train.isDerailed() || train.isPhysics()) {
            return;
        }
        boolean braking = isBraking(train.targetSpeed(), train.speed());
        double limitMps2 = train.brakeHold() ? TrainBrakes.BRAKE_DECEL
                : braking ? brakeLimit(train, train.targetSpeed(), train.speed())
                : tractionAccelLimit(train, ConsistPower.tractionMass(train, 1.0));
        train.setAccel(limitMps2 * MPS2_TO_BPT2);
    }

    @org.jetbrains.annotations.Nullable
    public static Vec3 axleDriveHeading(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return null;
        }
        if (!(container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        BearingAxleBlockEntity axle = bearingAxleIn(sub);
        Direction front = TrainSteering.axleFront(axle, sub);
        if (axle == null || front == null) {
            return null;
        }
        double rpm = axle.isOverStressed() ? 0.0 : axle.getSpeed();
        if (Math.abs(rpm) < 1.0e-3) {
            return null;
        }
        return new Vec3(front.getStepX(), 0.0, front.getStepZ()).scale(Math.signum(rpm));
    }

    public static double axleSign(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return train.axleSign();
        }
        if (!(container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return train.axleSign();
        }
        return axleSign(train, sub, bearingAxleIn(sub));
    }

    public static double axleRpm(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return 0.0;
        }
        if (!(container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return 0.0;
        }
        BearingAxleBlockEntity axle = bearingAxleIn(sub);
        return axle == null || axle.isOverStressed() ? 0.0 : axle.getSpeed();
    }

    public static double axleSign(SableTrain train, ServerSubLevel sub, BearingAxleBlockEntity axle) {
        Direction front = TrainSteering.axleFront(axle, sub);
        RailCarriage rail = train.leadingBogeyRail();
        if (front == null || rail == null) {
            return train.axleSign();
        }
        if (train.isPoseSettling()) {
            return train.axleSign();
        }
        Vec3 forward = TrainRailAdvance.bodyForward(rail);
        if (forward == null) {
            return train.axleSign();
        }
        double sign = front.getStepX() * forward.x + front.getStepZ() * forward.z < 0.0 ? -1.0 : 1.0;
        train.setAxleSign(sign);
        return sign;
    }

    static void applyPropulsion(SableTrain train) {
        if (train.isDerailed()) {
            train.setPowered(false);
            train.setWheelDrive(derailedWheelDrive(train));
            return;
        }
        // wheeldrive is only consumed while derailed, clear it here so a rerailed car never carries a stale value
        train.setWheelDrive(0.0);
        if (train.brakeHold()) {
            train.setPowered(false);
            train.setTargetSpeed(0.0);
            if (Math.abs(train.speed()) <= STANDSTILL_SPEED) {
                train.setSpeed(0.0);
            }
            return;
        }
        if (consistHeldByStaff(train)) {
            train.setPowered(false);
            train.setTargetSpeed(0.0);
            return;
        }
        ConsistPower.Result power = ConsistPower.resultFor(train);
        boolean driven = power != null && ConsistPower.isManual(train) && !power.axles().isEmpty();
        if (power == null || !power.anyPowered() && !driven) {
            train.setPowered(false);
            train.setTargetSpeed(0.0);
            return;
        }
        train.setPowered(true);
        train.setTargetSpeed(ConsistPower.targetFor(train));
    }

    private static boolean consistHeldByStaff(SableTrain train) {
        ServerLevel level = train.level();
        if (level == null || train.car() == null) {
            return false;
        }
        for (UUID id : TrainConsist.connectedSubLevels(level, train.car().subLevelId())) {
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            if (sub != null && ExternalForceDerail.staffHolds(sub)) {
                return true;
            }
        }
        return false;
    }

    public static double createTopSpeedMps() {
        return AllConfigs.server().trains.trainTopSpeed.get();
    }

    static double bearingAxleCapMps() {
        double cfg = LoconauticsConfig.BEARING_AXLE_MAX_SPEED.get();
        return cfg < 0.0 ? createTopSpeedMps() : cfg;
    }

    public static double bearingAxleCapBpt() {
        return bearingAxleCapMps() / 20.0;
    }

    static double derailedWheelDrive(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return 0.0;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return 0.0;
        }
        BearingAxleBlockEntity axle = bearingAxleIn(sub);
        if (axle == null) {
            return 0.0;
        }
        if (!SableTrainDriver.anyBogeyAlive(sub, car)) {
            return 0.0;
        }
        double rpm = axle.isOverStressed() ? 0.0 : axle.getSpeed();
        double cap = speedCeilingMps() / 20.0;
        return Math.max(-cap, Math.min(cap, AxleGearing.gearedSpeed(bearingAxleCapBpt(), axle.gearRatio(), rpm)));
    }

    static BearingAxleBlockEntity findBearingAxle(SableTrain train, ServerSubLevelContainer container) {
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        return bearingAxleIn(sub);
    }

    static double liveMass(ServerSubLevel sub) {
        return TrainConsist.subLevelMass(sub);
    }

    static BearingAxleBlockEntity bearingAxleIn(ServerSubLevel sub) {
        return com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity.resolve(sub);
    }

    private TrainPropulsion() {
    }
}
