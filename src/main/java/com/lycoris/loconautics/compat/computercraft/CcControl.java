package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import com.lycoris.loconautics.content.schedule.RouteDistance;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.drive.AxleGearing;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.drive.TrainSteeringAccessor;
import com.lycoris.loconautics.trainphysics.door.TrainDoorsAccessor;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CcControl {

    public static final String PARKED_EVENT = "train_parked";
    public static final String STOPPED_EVENT = "train_stopped";

    private static final Map<UUID, CcControl> ACTIVE = new HashMap<>();
    private static int lastParkId;
    private static int lastStopId;
    private static final int DRIVETRAIN_RECHECK_TICKS = 20;
    private static final int DRIVE_MIN_TICKS = 5;
    private static final float RPM_STEP = 8.0f;

    private final UUID trainId;

    private Double targetSpeed;
    private Double accel;
    private Double brakeScale;
    private Integer steer;
    private Boolean doors;
    private GlobalStation parkStation;
    private TrackGraphLocation parkLocation;
    private List<Couple<TrackNode>> parkRoute;
    private int parkDirection;
    private int terminalSense;
    private int brakeSense;
    private int parkId;
    private boolean stopping;
    private boolean emergencyStop;
    private double stopRemaining = Double.NaN;
    private int stopSense;
    private int stopId;
    private boolean drivetrainOk;
    private LocomotiveHardware.Drivetrain drivetrain;
    private long drivetrainCheckedAt = Long.MIN_VALUE / 2;
    private long driveSetAt = Long.MIN_VALUE / 2;

    private CcControl(UUID trainId) {
        this.trainId = trainId;
    }

    public static CcControl claim(UUID trainId) {
        return ACTIVE.computeIfAbsent(trainId, CcControl::new);
    }

    public static CcControl claimClean(UUID trainId) {
        CcControl fresh = new CcControl(trainId);
        CcControl replaced = ACTIVE.put(trainId, fresh);
        if (replaced != null) {
            replaced.abandonPark();
            replaced.clearTransmission();
        }
        return fresh;
    }

    public static CcControl get(UUID trainId) {
        return ACTIVE.get(trainId);
    }

    public static boolean held(UUID trainId) {
        return ACTIVE.containsKey(trainId);
    }

    public static void release(UUID trainId) {
        CcControl released = ACTIVE.remove(trainId);
        if (released != null) {
            com.lycoris.loconautics.LoconauticsConstants.LOGGER.info("[ccsteer] dropped control of {} steer={}", trainId, released.steer);
            released.abandonPark();
            released.clearTransmission();
        }
    }

    public static void retain(java.util.Set<UUID> live) {
        ACTIVE.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().clearTransmission();
            return true;
        });
    }

    public static boolean isDriving(UUID trainId) {
        CcControl control = ACTIVE.get(trainId);
        if (control == null) {
            return false;
        }
        return control.isParking()
                || (control.targetSpeed != null && Math.abs(control.targetSpeed) > 1.0e-6);
    }

    public static boolean holdsDoors(UUID trainId) {
        CcControl control = ACTIVE.get(trainId);
        return control != null && control.doors != null;
    }

    public void setTargetSpeed(double speed) {
        this.targetSpeed = speed;
        abandonPark();
        abandonStop();
    }

    // parkatcoord blocks on this event, so every way a park ends has to send it
    private void abandonPark() {
        if (!isParking()) {
            return;
        }
        parkStation = null;
        parkLocation = null;
        CcEvents.queue(trainId, PARKED_EVENT, false, parkId);
    }

    public int stopAt(SableTrain train, boolean emergency, double distance) {
        abandonPark();
        abandonStop();
        stopId = ++lastStopId;
        stopping = true;
        emergencyStop = emergency;
        stopRemaining = distance;
        double speed = train.speed();
        stopSense = Math.abs(speed) > 1.0e-4 ? (int) Math.signum(speed) : (int) Math.signum(train.targetSpeed());
        brakeScale = emergency ? 1.0 : null;
        return stopId;
    }

    public boolean isStopping() {
        return stopping;
    }

    private void abandonStop() {
        if (!stopping) {
            return;
        }
        stopping = false;
        CcEvents.queue(trainId, STOPPED_EVENT, stopId, false);
    }

    private void driveStop(SableTrain train) {
        double speed = Math.abs(train.speed());
        boolean measured = !Double.isNaN(stopRemaining);
        if (measured) {
            stopRemaining -= speed;
        }
        boolean overrun = measured && stopRemaining <= 0.0;
        boolean settled = speed <= StationLanding.PARK_SPEED
                && (!measured || overrun || stopRemaining <= StationLanding.LANDING_RANGE);
        if (stopSense == 0 || settled) {
            stopping = false;
            targetSpeed = 0.0;
            brakeScale = 1.0;
            StationLanding.applySnap(train, 0.0);
            CcEvents.queue(train, STOPPED_EVENT, stopId, true);
            return;
        }
        if (emergencyStop || overrun) {
            targetSpeed = 0.0;
            brakeScale = 1.0;
            return;
        }
        brakeScale = null;
        targetSpeed = measured ? stopSense * StationLanding.approachSpeed(train, stopRemaining) : 0.0;
        train.setServoBrake(true);
    }

    public void setAccel(Double accel) {
        this.accel = accel;
    }

    public void setBrakeScale(Double scale) {
        this.brakeScale = scale;
    }

    public void setSteer(Integer steer) {
        this.steer = steer;
    }

    public Integer steer() {
        return steer;
    }

    public void setDoors(Boolean doors) {
        this.doors = doors;
    }

    public int parkAt(SchedulePath path, RailCarriage nose) {
        this.parkId = ++lastParkId;
        this.parkStation = path.station();
        this.parkLocation = path.target();
        this.parkRoute = path.route();
        this.parkDirection = path.direction();
        this.terminalSense = 0;
        this.brakeSense = 0;
        this.brakeScale = null;
        if (nose != null && path.followsRoute()) {
            nose.setPath(path.route());
        }
        return parkId;
    }

    public boolean isParking() {
        return parkLocation != null;
    }

    public static void apply(SableTrain train) {
        CcControl control = ACTIVE.get(train.id());
        if (control == null) {
            return;
        }
        if (control.stopping) {
            control.driveStop(train);
        }
        // a park hold outranks the computer, otherwise the car sits still while its drive fx run
        boolean held = train.brakeHold();
        boolean driven = control.drivetrainWorks(train);
        if (control.isParking() && !held) {
            if (driven) {
                control.drivePark(train);
            } else {
                control.stop(train);
            }
        }
        if (control.targetSpeed != null && !held) {
            double desired = driven ? control.targetSpeed : 0.0;
            if (driven) {
                control.driveTransmission(train, desired);
            }
            train.setTargetSpeed(capped(train.targetSpeed(), desired));
        }
        if (control.accel != null && !held && driven) {
            train.setAccel(control.accel);
        }
        if (control.brakeScale != null) {
            train.setBrakeScale(control.brakeScale);
            train.setBraked(control.brakeScale > 0.0);
        }
        if (control.doors != null) {
            TrainDoorsAccessor.setDoors(train, control.doors);
        }
        if (control.steer != null) {
            TrainSteeringAccessor.steer(train, control.steer);
        }
    }

    private boolean drivetrainWorks(SableTrain train) {
        long now = train.level().getGameTime();
        if (now - drivetrainCheckedAt >= DRIVETRAIN_RECHECK_TICKS) {
            drivetrainCheckedAt = now;
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            drivetrain = sub == null ? null : LocomotiveHardware.drivetrain(sub);
            drivetrainOk = drivetrain != null && drivetrain.ok();
            if (!drivetrainOk) {
                clearTransmission();
                if (targetSpeed != null) {
                    targetSpeed = 0.0;
                }
            }
        }
        return drivetrainOk;
    }

    private void clearTransmission() {
        if (drivetrain != null && drivetrain.transmission() != null) {
            drivetrain.transmission().clearComputerDrive();
        }
    }

    private void driveTransmission(SableTrain train, double desired) {
        TransmissionBlockEntity transmission = drivetrain.transmission();
        double input = transmission.getSpeed();
        boolean running = transmission.isComputerDriven();
        float rpm = 0f;
        boolean reversed = running && transmission.isComputerReversed();
        if (desired != 0.0) {
            double perRpm = AxleGearing.gearedSpeed(TrainPropulsion.bearingAxleCapBpt(),
                    drivetrain.axle().gearRatio(), 1.0);
            if (Math.abs(input) < 1.0e-3 || perRpm <= 1.0e-9) {
                return;
            }
            rpm = (float) Math.min(AxleGearing.FULL_RPM, Math.abs(desired) / perRpm);
            double outSign = Math.signum(desired) * TrainPropulsion.axleSign(train);
            reversed = outSign * Math.signum(input) < 0.0;
        }
        if (running) {
            boolean sameStep = Math.abs(rpm - transmission.getComputerRpm()) < RPM_STEP
                    && (rpm == 0f) == (transmission.getComputerRpm() == 0f);
            if ((sameStep && reversed == transmission.isComputerReversed())
                    || train.level().getGameTime() - driveSetAt < DRIVE_MIN_TICKS) {
                return;
            }
        }
        driveSetAt = train.level().getGameTime();
        transmission.setComputerDrive(rpm, reversed);
    }

    private static double capped(double physics, double desired) {
        if (desired == 0.0 || Math.signum(physics) != Math.signum(desired)) {
            return 0.0;
        }
        return Math.signum(desired) * Math.min(Math.abs(physics), Math.abs(desired));
    }

    private void drivePark(SableTrain train) {
        RailCarriage carriage = TrainStations.noseRail(train);
        TrackGraph graph = carriage == null ? null : carriage.graph();
        TravellingPoint nose = TrainStations.nosePoint(train);
        if (graph == null || nose == null || nose.edge == null) {
            stop(train);
            return;
        }
        double onEdge = RouteDistance.signedOnEdge(nose, parkLocation);
        double remaining = RouteDistance.remaining(nose, graph, parkRoute, parkLocation);
        if (Double.isNaN(onEdge) && remaining == RouteDistance.UNKNOWN) {
            stop(train);
            return;
        }
        double error = remaining + StationLanding.parkOffset;
        if (!Double.isNaN(onEdge)) {
            if (terminalSense == 0) {
                terminalSense = onEdge >= 0.0 ? 1 : -1;
            }
            error = terminalSense * onEdge + StationLanding.parkOffset;
        }
        double gap = Math.abs(error);
        if (brakeSense == 0) {
            brakeSense = error >= 0.0 ? 1 : -1;
        }
        if (StationLanding.canSnap(train, gap) || StationLanding.wentPast(error, brakeSense)) {
            arrive(train, parkDirection * error, true);
            return;
        }
        if (brakeSense < 0) {
            arrive(train, 0.0, false);
            return;
        }
        targetSpeed = parkDirection * ConsistNose.frameFlip(train) * StationLanding.approachSpeed(train, gap);
        train.setServoBrake(true);
    }

    private void arrive(SableTrain train, double signedGap, boolean onTarget) {
        GlobalStation station = parkStation;
        double direction = parkDirection;
        land(train, signedGap, onTarget);
        if (station == null) {
            return;
        }
        train.setCurrentStation(station);
        train.setStationDirection(direction);
        train.setDistanceToStation(0.0);
        train.setStationState(SableTrain.StationState.STOPPED);
        train.setAtStation(station);
    }

    private void land(SableTrain train, double signedGap, boolean onTarget) {
        parkStation = null;
        parkLocation = null;
        RailCarriage nose = TrainStations.noseRail(train);
        if (parkRoute != null && nose != null) {
            nose.clearPath();
        }
        parkRoute = null;
        targetSpeed = 0.0;
        brakeScale = 1.0;
        if (Double.isNaN(signedGap)) {
            StationLanding.applySnap(train, 0.0);
        } else {
            StationLanding.snapConsist(train, signedGap);
        }
        CcEvents.queue(train, PARKED_EVENT, onTarget, parkId);
    }

    private void stop(SableTrain train) {
        land(train, Double.NaN, false);
    }
}
