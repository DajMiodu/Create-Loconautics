package com.lycoris.loconautics.trainphysics;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.compat.computercraft.CcEvents;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spacing.CarExtent;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

public final class SableTrain {

    public record Bogey(BlockPos localPos, RailCarriage rail) {}

    public record Car(UUID subLevelId, RailCarriage carriage, List<Bogey> bogeys,
                      Vector3dc localLead, Vector3dc localTrail) {

        public List<Bogey> chain() {
            List<Bogey> railed = bogeys.stream().filter(b -> b.rail() != null).toList();
            List<Bogey> chain = railed.stream().filter(b -> !b.rail().isIndependent()).toList();
            return chain.isEmpty() ? railed : chain;
        }
    }

    public enum StationState {
        RUNNING, STOPPING, STOPPED, DEPARTING
    }

    public enum Residency {
        EMBODIED, GHOST
    }

    private final UUID id;
    private ServerLevel level;
    private Car car;
    private final boolean physics;

    private UUID owner;
    private double speed;
    private double lastRollingSpeed;
    private double targetSpeed;
    private double accel = 0.01;
    private double measuredAccel;
    private net.minecraft.core.Direction steerIntent;
    private double brakeScale = 1.0;
    private double handbrake;
    private boolean powered;
    private boolean derailed;
    private String derailReason = "";
    private Residency residency = Residency.EMBODIED;
    // only readable while the body is live, so it is grabbed at unload and persisted, without it a ghost cannot find its body again
    private GlobalSavedSubLevelPointer bodyPointer;
    private net.minecraft.world.level.ChunkPos bodyChunk;
    private Vector3d lastKnownPos;
    // the coupled sub levels as of the last embodied tick, the coupler block entities sit inside the body and a ghost cannot ask them
    private final java.util.Set<UUID> consistCache = new java.util.LinkedHashSet<>();
    private boolean scheduleDriven;
    // measured off the body while it is loaded, a ghost has no body to measure
    private CarExtent extent;
    private double ghostCruise;
    private double axleSign = 1.0;
    private int poseSettle;
    private Vector3d pendingKnockback;
    // bogeys that ran off the rail end while the car is still otherwise railed driving state only, deliberately not persisted, rederived each tick
    private final java.util.Set<BlockPos> offTrackBogeys = new java.util.HashSet<>();
    // creates bogey block has no front, only an axis, so a player set front lives here keyed by bogey local position
    private final java.util.Map<BlockPos, net.minecraft.core.Direction> bogeyFront = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, Long> offTrackSince = new java.util.HashMap<>();
    private double wheelDrive;
    private final PoseTweak poseTweak = new PoseTweak();
    private int mapColorIndex;
    // empty means creates default, stored by id so a changed mod set cannot shift it like an index would
    private String iconType = "";
    // the name lives on the sub level, this is the last one read off it so a ghost or a body mid crossing keeps it
    private String name = "";
    private boolean braked;
    private boolean brakeHard;
    private boolean brakeHold;

    private StationState stationState = StationState.RUNNING;
    private double distanceToStation = Double.MAX_VALUE;
    private double approachTotal;
    private int dwellTicks;
    private GlobalStation currentStation;
    private GlobalStation sightedStation;
    private double sightedDistance = Double.MAX_VALUE;
    private GlobalStation atStation;
    private Vector3dc atStationPos;
    private double stationDirection = 1.0;
    private boolean doorsOpen;

    public SableTrain(UUID id, ServerLevel level, Car car, boolean physics) {
        this.id = id;
        this.level = level;
        this.car = car;
        this.physics = physics;
    }

    public boolean isPhysics() {
        return physics;
    }

    public UUID id() {
        return id;
    }

    public ServerLevel level() {
        return level;
    }

    // only the portal crossing writes this, everything else in trainphysics reads level and expects it stable
    public void setLevel(ServerLevel newLevel) {
        this.level = newLevel;
    }

    public Car car() {
        return car;
    }

    public void relocate(RailCarriage newCarriage, List<Bogey> newBogeys) {
        relocate(newCarriage, newBogeys, car.localLead(), car.localTrail());
    }

    public void relocate(Car rebuilt) {
        relocate(rebuilt.carriage(), rebuilt.bogeys(),
                rebuilt.localLead() != null ? rebuilt.localLead() : car.localLead(),
                rebuilt.localTrail() != null ? rebuilt.localTrail() : car.localTrail());
    }

    private void relocate(RailCarriage newCarriage, List<Bogey> newBogeys, Vector3dc lead, Vector3dc trail) {
        traceBogeyDrop("relocate", newBogeys.size());
        this.car = new Car(car.subLevelId(), newCarriage, newBogeys, lead, trail);
        // a bogey that left with a split island would otherwise stay flagged here, smoking and failing to rerail forever
        offTrackBogeys.removeIf(pos -> !hasBogeyAt(newBogeys, pos));
        offTrackSince.keySet().removeIf(pos -> !hasBogeyAt(newBogeys, pos));
        this.speed = 0.0;
        this.targetSpeed = 0.0;
        this.stationState = StationState.RUNNING;
        this.distanceToStation = Double.MAX_VALUE;
        this.dwellTicks = 0;
        this.currentStation = null;
    }

    public void keepStop(StationState state, GlobalStation station, double distance, int dwell) {
        this.stationState = state;
        this.currentStation = station;
        this.distanceToStation = distance;
        this.dwellTicks = dwell;
    }

    private void traceBogeyDrop(String how, int newCount) {
        if (car == null || newCount >= car.bogeys().size()) {
            return;
        }
        StringBuilder callers = new StringBuilder();
        StackWalker.getInstance().walk(frames -> frames.skip(2).limit(5).map(f -> f.getClassName()
                .substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName() + ":" + f.getLineNumber())
                .toList()).forEach(s -> callers.append(s).append(" < "));
    }

    public void replaceCar(Car newCar) {
        traceBogeyDrop("replaceCar", newCar.bogeys().size());
        this.car = newCar;
    }

    public void setBogeys(List<Bogey> newBogeys) {
        traceBogeyDrop("setBogeys", newBogeys.size());
        this.car = new Car(car.subLevelId(), car.carriage(), newBogeys, car.localLead(), car.localTrail());
        offTrackBogeys.removeIf(pos -> !hasBogeyAt(newBogeys, pos));
        bogeyFront.keySet().removeIf(pos -> !hasBogeyAt(newBogeys, pos));
    }

    private static boolean hasBogeyAt(List<Bogey> bogeys, BlockPos localPos) {
        for (Bogey bogey : bogeys) {
            if (bogey.localPos().equals(localPos)) {
                return true;
            }
        }
        return false;
    }

    public RailCarriage leadingBogeyRail() {
        if (car == null || car.bogeys().isEmpty()) {
            return null;
        }
        for (int i = car.bogeys().size() - 1; i >= 0; i--) {
            Bogey bogey = car.bogeys().get(i);
            if (!offTrackBogeys.contains(bogey.localPos())) {
                return bogey.rail();
            }
        }
        return car.bogeys().get(car.bogeys().size() - 1).rail();
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double lastRollingSpeed() {
        return lastRollingSpeed;
    }

    public void setLastRollingSpeed(double lastRollingSpeed) {
        this.lastRollingSpeed = lastRollingSpeed;
    }

    public void setTargetSpeed(double targetSpeed) {
        this.targetSpeed = targetSpeed;
    }

    public double targetSpeed() {
        return targetSpeed;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean doorsOpen() {
        return doorsOpen;
    }

    public void setDoorsOpen(boolean doorsOpen) {
        this.doorsOpen = doorsOpen;
    }

    public boolean isDerailed() {
        return derailed;
    }

    public boolean isTrackless() {
        return car != null && car.carriage() == null;
    }

    public void setDerailed(boolean derailed) {
        setDerailed(derailed, "track_missing");
    }

    public void setDerailed(boolean derailed, String statusKey) {
        if (!setDerailedQuiet(derailed)) {
            return;
        }
        derailReason = derailed ? statusKey : "";
        if (derailed) {
            TrainStatusChat.derailed(this, statusKey);
        } else {
            TrainStatusChat.backOnTrack(this);
        }
        CcEvents.queue(this, derailed ? "train_derailed" : "train_rerailed");
    }

    public void setDerailReason(String reason) {
        derailReason = reason;
    }

    public boolean derailedAtTrackEnd() {
        return "end_of_track".equals(derailReason);
    }

    public boolean setDerailedQuiet(boolean derailed) {
        if (this.derailed == derailed) {
            return false;
        }
        this.derailed = derailed;
        if (!derailed) {
            offTrackBogeys.clear();
        }
        SableTrainPersistence.persist(this);
        return true;
    }

    public void queueKnockback(Vector3d worldDeltaVelocity) {
        this.pendingKnockback = worldDeltaVelocity;
    }

    public Vector3d takeKnockback() {
        Vector3d v = this.pendingKnockback;
        this.pendingKnockback = null;
        return v;
    }

    public boolean isBogeyOffTrack(BlockPos localPos) {
        return offTrackBogeys.contains(localPos);
    }

    public boolean markBogeyOffTrack(BlockPos localPos) {
        if (level != null) {
            offTrackSince.put(localPos, level.getGameTime());
        }
        return offTrackBogeys.add(localPos);
    }

    public void markBogeyAwaitingRail(BlockPos localPos) {
        offTrackSince.remove(localPos);
        offTrackBogeys.add(localPos);
    }

    public boolean bogeyRerailReady(BlockPos localPos, int cooldownTicks) {
        Long since = offTrackSince.get(localPos);
        return since == null || level == null || level.getGameTime() - since >= cooldownTicks;
    }


    public void clearOffTrackBogeys() {
        offTrackBogeys.clear();
    }

    public boolean clearOffTrackBogey(BlockPos localPos) {
        return offTrackBogeys.remove(localPos);
    }

    public net.minecraft.core.Direction bogeyFrontOverride(BlockPos localPos) {
        return bogeyFront.get(localPos);
    }

    public void setBogeyFrontOverride(BlockPos localPos, net.minecraft.core.Direction front) {
        if (front == null) {
            bogeyFront.remove(localPos);
        } else {
            bogeyFront.put(localPos, front);
        }
        SableTrainPersistence.persist(this);
    }

    public void restoreBogeyFrontOverrides(java.util.Map<BlockPos, net.minecraft.core.Direction> fronts) {
        bogeyFront.clear();
        bogeyFront.putAll(fronts);
    }

    public java.util.Map<BlockPos, net.minecraft.core.Direction> bogeyFrontOverrides() {
        return java.util.Collections.unmodifiableMap(bogeyFront);
    }

    public int offTrackBogeyCount() {
        return offTrackBogeys.size();
    }

    public boolean hasOffTrackBogey() {
        return !offTrackBogeys.isEmpty();
    }

    private final java.util.Map<BlockPos, Double> drivenBogeys = new java.util.LinkedHashMap<>();

    private final java.util.Map<BlockPos, Double> wheelSurface = new java.util.LinkedHashMap<>();

    private final java.util.Map<BlockPos, Integer> wheelStall = new java.util.LinkedHashMap<>();

    private final java.util.Set<BlockPos> slippingBogeys = new java.util.LinkedHashSet<>();

    private Vector3d lastBodyPos;

    public java.util.Map<BlockPos, Double> wheelSurface() {
        return wheelSurface;
    }

    public java.util.Map<BlockPos, Integer> wheelStall() {
        return wheelStall;
    }

    public java.util.Set<BlockPos> slippingBogeys() {
        return slippingBogeys;
    }

    public Vector3d lastBodyPos() {
        return lastBodyPos;
    }

    public void setLastBodyPos(Vector3d pos) {
        this.lastBodyPos = pos;
    }

    public java.util.Map<BlockPos, Double> drivenBogeys() {
        return drivenBogeys;
    }

    public void setDrivenBogeys(java.util.Map<BlockPos, Double> driven) {
        drivenBogeys.clear();
        if (driven != null) {
            drivenBogeys.putAll(driven);
        }
    }

    public double wheelDrive() {
        return wheelDrive;
    }

    public void setWheelDrive(double wheelDrive) {
        this.wheelDrive = wheelDrive;
    }

    public PoseTweak poseTweak() {
        return poseTweak;
    }

    public String iconType() {
        return iconType;
    }

    public void setIconType(String iconType) {
        this.iconType = iconType == null ? "" : iconType;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public int mapColorIndex() {
        return mapColorIndex;
    }

    public void setMapColorIndex(int mapColorIndex) {
        this.mapColorIndex = Mth.clamp(mapColorIndex, 0, 15);
    }

    private double slipFactor = 1.0;

    public double slipFactor() {
        return slipFactor;
    }

    public void setSlipFactor(double slipFactor) {
        this.slipFactor = slipFactor;
    }

    // which the free coasting case must not claim or every loose wagon would drag itself to a halt
    public boolean servoBrake() {
        return servoBrake;
    }

    public void setServoBrake(boolean servoBrake) {
        this.servoBrake = servoBrake;
    }

    private boolean servoBrake;

    public boolean isFollowing() {
        return following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    private boolean following;

    public boolean isBraked() {
        return braked;
    }

    public void setBraked(boolean braked) {
        this.braked = braked;
    }

    public boolean brakeHold() {
        return brakeHold;
    }

    public void setBrakeHold(boolean hold) {
        this.brakeHold = hold;
    }

    public boolean brakeHard() {
        return brakeHard;
    }

    public void setBrakeHard(boolean hard) {
        this.brakeHard = hard;
    }

    public net.minecraft.core.Direction steerIntent() {
        return steerIntent;
    }

    public void setSteerIntent(net.minecraft.core.Direction intent) {
        this.steerIntent = intent;
    }

    public double brakeScale() {
        return brakeScale;
    }

    public double handbrake() {
        return handbrake;
    }

    public void setHandbrake(double handbrake) {
        this.handbrake = handbrake;
    }

    public void setBrakeScale(double scale) {
        this.brakeScale = Math.max(0.0, Math.min(1.0, scale));
    }

    public static final int BRAKE_PULSE_TICKS = 10;
    private int brakePulse;

    public void armBrakePulse() {
        brakePulse = BRAKE_PULSE_TICKS;
    }

    public boolean brakePulseActive() {
        return brakePulse > 0;
    }

    public void tickBrakePulse() {
        if (brakePulse > 0) {
            brakePulse--;
        }
    }

    private boolean scheduleHold;

    public boolean scheduleHold() {
        return scheduleHold;
    }

    public void setScheduleHold(boolean hold) {
        if (hold != this.scheduleHold) {
            String caller = StackWalker.getInstance().walk(frames -> frames.skip(1).findFirst()
                    .map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "."
                            + frame.getMethodName() + ":" + frame.getLineNumber()).orElse("?"));
            LoconauticsConstants.LOGGER.info("[maneuver] hold {} -> {} on {} speed {} by {}", this.scheduleHold, hold,
                    id().toString().substring(0, 8), String.format("%.3f", speed()), caller);
        }
        this.scheduleHold = hold;
    }

    public StationState stationState() {
        return stationState;
    }

    public void setStationState(StationState state) {
        StationState previous = this.stationState;
        this.stationState = state;
        if (previous == state) {
            return;
        }
        if (state != StationState.RUNNING) {
            setSighted(null, Double.MAX_VALUE);
        }
        if (state == StationState.STOPPED) {
            CcEvents.queue(this, "train_arrived", stationLabel());
        } else if (previous == StationState.STOPPED) {
            CcEvents.queue(this, "train_departed", stationLabel());
        }
    }

    private String stationLabel() {
        GlobalStation station = stoppedAt();
        return station == null ? "" : station.name;
    }

    public boolean isAtStation() {
        return stationState == StationState.STOPPING || stationState == StationState.STOPPED;
    }

    public double distanceToStation() {
        return distanceToStation;
    }

    public void setDistanceToStation(double distance) {
        this.distanceToStation = distance;
    }

    public GlobalStation sightedStation() {
        return sightedStation;
    }

    public double sightedDistance() {
        return sightedDistance;
    }

    public void setSighted(GlobalStation station, double distance) {
        this.sightedStation = station;
        this.sightedDistance = station == null ? Double.MAX_VALUE : distance;
    }

    public double approachTotal() {
        return approachTotal;
    }

    public void setApproachTotal(double total) {
        this.approachTotal = total;
    }

    public int dwellTicks() {
        return dwellTicks;
    }

    public void setDwellTicks(int ticks) {
        this.dwellTicks = ticks;
    }

    public GlobalStation currentStation() {
        return currentStation;
    }

    public void setCurrentStation(GlobalStation station) {
        this.currentStation = station;
    }

    // current station is not saved, a train parked before a restart only has the station it is standing at
    public GlobalStation stoppedAt() {
        return currentStation != null ? currentStation : atStation;
    }

    public GlobalStation atStation() {
        return atStation;
    }

    public void setAtStation(GlobalStation station) {
        this.atStation = station;
        if (station == null) {
            this.atStationPos = null;
        }
    }

    public Vector3dc atStationPos() {
        return atStationPos;
    }

    public void setAtStationPos(Vector3dc pos) {
        this.atStationPos = pos;
    }

    public double stationDirection() {
        return stationDirection;
    }

    public void setStationDirection(double direction) {
        this.stationDirection = direction;
    }

    public void setAccel(double accel) {
        this.accel = Math.abs(accel);
    }

    public double accel() {
        return accel;
    }

    public double measuredAccel() {
        return measuredAccel;
    }

    public Residency residency() {
        return residency;
    }

    public boolean isGhost() {
        return residency == Residency.GHOST;
    }

    public void setResidency(Residency residency) {
        if (this.residency != residency) {
            // a ghost never ticks the pulse down, so it braked only the lead on load
            brakePulse = 0;
        }
        this.residency = residency;
    }

    public GlobalSavedSubLevelPointer bodyPointer() {
        return bodyPointer;
    }

    public void setBodyPointer(GlobalSavedSubLevelPointer pointer) {
        this.bodyPointer = pointer;
    }

    public net.minecraft.world.level.ChunkPos bodyChunk() {
        return bodyChunk;
    }

    public void setBodyChunk(net.minecraft.world.level.ChunkPos chunk) {
        this.bodyChunk = chunk;
    }

    public Vector3dc lastKnownPos() {
        return lastKnownPos;
    }

    public void setLastKnownPos(Vector3dc pos) {
        this.lastKnownPos = pos == null ? null : new Vector3d(pos);
    }

    public java.util.Set<UUID> consistCache() {
        return java.util.Collections.unmodifiableSet(consistCache);
    }

    public void setConsistCache(java.util.Collection<UUID> ids) {
        consistCache.clear();
        if (ids != null) {
            consistCache.addAll(ids);
        }
    }

    public CarExtent extent() {
        return extent;
    }

    public void setExtent(CarExtent extent) {
        this.extent = extent;
    }

    public boolean isScheduleDriven() {
        return scheduleDriven;
    }

    public double ghostCruise() {
        return ghostCruise;
    }

    public void setGhostCruise(double cruise) {
        this.ghostCruise = Math.abs(cruise);
    }

    public double axleSign() {
        return axleSign;
    }

    public void setAxleSign(double sign) {
        this.axleSign = sign < 0.0 ? -1.0 : 1.0;
    }

    public boolean isPoseSettling() {
        return poseSettle > 0;
    }

    public void beginPoseSettle(int ticks) {
        this.poseSettle = ticks;
    }

    public void tickPoseSettle() {
        if (poseSettle > 0) {
            poseSettle--;
        }
    }

    public void setScheduleDriven(boolean scheduleDriven) {
        this.scheduleDriven = scheduleDriven;
    }

    public void tickMotion() {
        double before = speed;
        // sole speed authority sabletraindriver authors the cars pose from the rail using this speed physics never feeds back into it
        if (speed < targetSpeed) {
            speed = Math.min(targetSpeed, speed + accel);
        } else if (speed > targetSpeed) {
            speed = Math.max(targetSpeed, speed - accel);
        }
        measuredAccel = speed - before;
    }
}
