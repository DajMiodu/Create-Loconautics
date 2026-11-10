package com.lycoris.loconautics.trainphysics.station;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.trainmail.TrainMailTransfer;
import com.lycoris.loconautics.network.LoconauticsNetwork;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.SableTrain.StationState;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainBrakes;
import com.lycoris.loconautics.trainphysics.door.TrainDoors;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.theme.Color;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrainStations {

    public static final double STATION_PARK_RADIUS = 3.0;
    static final int STATION_NAME_COLOR = 7358000;
    static final double LEFT_STATION_DISTANCE_SQR = 16.0;
    static final int DOOR_SETTLE_TICKS = 5;
    static final double DOOR_CLOSE_SPEED = 0.02;
    static final int APPROACH_COLOR = 0xFFC244;
    static final int ARRIVED_COLOR = 0x529915;
    static final int REMAINING_COLOR = 0x544D45;
    // a glued rapier body never reads a hard zero, and 0.001 bpt sits inside its own jitter, so a car standing on
    static final double DEPART_SPEED = 0.02;
    static final int PARK_SETTLE_TICKS = 5;

    public static int stationTickCounter;

    private static final Map<UUID, SableTrain> OWNERS = new HashMap<>();
    private static int ownersTick = Integer.MIN_VALUE;

    record StationTarget(GlobalStation station, double distance, double direction) {}

    public static void tickStation(SableTrain train, AnalogControllerBlockEntity controller) {
        SableTrain owner = stopOwner(train);
        if (owner != train) {
            mirrorStop(train, owner);
            train.setDwellTicks(train.stationState() == StationState.STOPPED ? train.dwellTicks() + 1 : 0);
            if (!com.lycoris.loconautics.compat.computercraft.CcControl.holdsDoors(train.id())) {
                TrainDoors.setDoors(train, doorsShouldOpen(train));
            }
            return;
        }
        tickStationState(train, controller);
        train.setDwellTicks(train.stationState() == StationState.STOPPED ? train.dwellTicks() + 1 : 0);
        TrainMailTransfer.tick(train);
        if (!com.lycoris.loconautics.compat.computercraft.CcControl.holdsDoors(train.id())) {
            TrainDoors.setDoors(train, doorsShouldOpen(train));
        }
    }

    // and closing waits for the car to actually roll again, otherwise the doors slam back and forth every tick
    private static boolean doorsShouldOpen(SableTrain train) {
        if (train.stationState() == StationState.STOPPED) {
            return train.dwellTicks() >= DOOR_SETTLE_TICKS;
        }
        return train.doorsOpen() && Math.abs(train.speed()) <= DOOR_CLOSE_SPEED;
    }

    private static void tickStationState(SableTrain train, AnalogControllerBlockEntity controller) {
        AnalogControllerBlockEntity drive = SableTrainDriver.controllerIn(train);
        // applypropulsion drops ispowered under a brake hold, so a train that came to a stand fell out of the machine and never tried to park
        boolean attended = drive != null && (drive.hasUser() || drive.isScheduleControlled())
                || com.lycoris.loconautics.compat.computercraft.CcControl.held(train.id());
        // guard below threw it out of the machine every tick, so it never latched stopped again
        boolean parked = Math.abs(train.speed()) <= DEPART_SPEED
                && (train.stationState() == StationState.STOPPED || train.scheduleHold() || attended
                        || train.atStation() != null);
        boolean stopping = train.stationState() == StationState.STOPPING
                || train.stationState() == StationState.STOPPED;
        boolean standing = !train.isPoseSettling() && Math.abs(train.speed()) <= DEPART_SPEED;
        boolean guardTripped = train.isDerailed() || train.isPhysics()
                || (!train.isPowered() && !parked && !stopping && !standing);
        if (guardTripped) {
            if (train.isDerailed() || train.isPhysics()) {
                train.setSighted(null, Double.MAX_VALUE);
            } else {
                double sight = (train.speed() * train.speed()) / (2.0 * StationLanding.DECELERATION);
                StationTarget coasting = scanAhead(train, Math.max(32.0, 1.5 * sight), null);
                train.setSighted(coasting == null ? null : coasting.station(),
                        coasting == null ? Double.MAX_VALUE : coasting.distance());
            }
            if (train.stationState() != StationState.RUNNING) {
                train.setStationState(StationState.RUNNING);
                train.setCurrentStation(null);
                train.setDistanceToStation(Double.MAX_VALUE);
            }
            return;
        }

        boolean schedulePulling = train.isGhost()
                ? train.isScheduleDriven() && Math.abs(train.targetSpeed()) > 1.0e-6
                : drive != null && drive.isScheduleControlled() && drive.getCurrentPower() != 0;
        boolean pulling = schedulePulling
                || (!train.isGhost() && drive != null && drive.getCurrentPower() != 0)
                || com.lycoris.loconautics.compat.computercraft.CcControl.isDriving(train.id())
                || Math.abs(ConsistPower.targetFor(train)) > 1.0e-6;

        boolean spaceHeld = controller != null && controller.isSpaceHeld();
        // the snap moves the car up to stationparkradius blocks, an idling or shoved loco never asked for that
        boolean wantsStop = spaceHeld || train.scheduleHold() || train.isScheduleDriven();

        if (!pulling && train.stationState() != StationState.STOPPED
                && !TrainSignals.consistHeldAtRed(train)
                && Math.abs(train.targetSpeed()) <= 1.0e-6
                && Math.abs(train.speed()) <= DEPART_SPEED) {
            StationTarget near = scanStation(train, STATION_PARK_RADIUS, null, STATION_PARK_RADIUS);
            int heading = near == null || StationLanding.wheelOnMark(near.distance()) ? 0 : ConsistNose.senseOf(train);
            if (heading != 0) {
                // off the mark it only creeps forward into a station that accepts this side, never back or onto a blind side
                near = scoutDirection(noseRail(train), nosePoint(train), STATION_PARK_RADIUS, heading, null, 0.0);
            }
            if (near != null && (wantsStop || StationLanding.wheelOnMark(near.distance()))) {
                StationLanding.snapConsist(train, near.direction() * near.distance());
                train.setCurrentStation(near.station());
                train.setDistanceToStation(0.0);
                train.setStationDirection(near.direction());
                train.setStationState(StationState.STOPPED);
                train.setAtStation(near.station());
                return;
            }
        }

        boolean forwardHeld = controller != null && controller.isForwardHeld();
        boolean reverseHeld = controller != null && controller.isReverseHeld();
        ServerPlayer operator = controller != null ? SableTrainDriver.operatorOf(train, controller) : null;
        boolean resend = (stationTickCounter % 10) == 0;

        switch (train.stationState()) {
            case RUNNING -> {
                double sight = (train.speed() * train.speed()) / (2.0 * StationLanding.DECELERATION);
                StationTarget target = scanAhead(train, Math.max(32.0, 1.5 * sight), null);
                train.setSighted(target == null ? null : target.station(),
                        target == null ? Double.MAX_VALUE : target.distance());
                if (target == null || target.distance() < StationLanding.hardStopDistance(train)) {
                    return;
                }
                if (spaceHeld) {
                    train.setCurrentStation(target.station());
                    train.setDistanceToStation(target.distance());
                    train.setApproachTotal(target.distance());
                    train.setStationDirection(target.direction());
                    train.setStationState(StationState.STOPPING);
                    if (controller != null) {
                        controller.setLocked(false);
                    }
                } else if (operator != null && resend) {
                    sendApproachPrompt(operator, target.station());
                }
            }
            case STOPPING -> {
                if (!spaceHeld) {
                    train.setStationState(StationState.RUNNING);
                    train.setCurrentStation(null);
                    train.setDistanceToStation(Double.MAX_VALUE);
                    return;
                }
                double searchRange = Math.max(32.0, train.distanceToStation() + 8.0);
                double direction = train.stationDirection();
                StationTarget live = scoutDirection(noseRail(train), nosePoint(train), searchRange,
                        direction, train.currentStation(), 0.0);
                if (live == null) {
                    StationLanding.applySnap(train, 0.0);
                    if (controller != null) {
                        controller.setLocked(false);
                        controller.setCurrentPower(0);
                    }
                    train.setStationState(StationState.STOPPED);
                    train.setAtStation(train.currentStation());
                    return;
                }
                double distance = live.distance();
                train.setDistanceToStation(distance);
                // this took a while to get right, dont touch a plain sqrt2deceldistance curve overshot on curves since the body lags the rail cursor
                double mag = StationLanding.approachSpeed(train, distance);
                boolean snap = StationLanding.canSnap(train, distance);
                if (snap) {
                    StationLanding.snapConsist(train, direction * distance);
                    if (controller != null) {
                        controller.setLocked(false);
                        controller.setCurrentPower(0);
                    }
                    train.setStationState(StationState.STOPPED);
                    train.setAtStation(train.currentStation());
                    return;
                }
                double bodyDirection = direction * ConsistNose.frameFlip(train);
                // applypropulsion zeroed the target for an unpowered train, so the approach must not hand it back
                train.setTargetSpeed(train.isPowered() ? bodyDirection * mag : 0.0);
                train.setServoBrake(true);
                if (controller != null) {
                    controller.setCurrentPower((int) Math.signum(bodyDirection) * StationLanding.powerNotches(train, mag));
                }
                if (operator != null) {
                    sendApproachBar(operator, train, distance);
                }
            }
            case STOPPED -> {
                boolean commanded = pulling || forwardHeld || reverseHeld;
                if (!commanded) {
                    train.setTargetSpeed(0.0);
                    TrainBrakes.holdSoft(train);
                    if (controller != null) {
                        controller.setCurrentPower(0);
                    }
                }
                boolean rolled = train.dwellTicks() >= PARK_SETTLE_TICKS
                        && Math.abs(train.speed()) > DEPART_SPEED;
                if (commanded || rolled) {
                    if (operator != null && commanded) {
                        sendStationPrompt(operator, "loconautics.train.departing_from", train.currentStation());
                    }
                    train.setStationState(StationState.DEPARTING);
                    train.setCurrentStation(null);
                    train.setDistanceToStation(Double.MAX_VALUE);
                    return;
                }
                if (operator != null && resend) {
                    sendStationPrompt(operator, "loconautics.train.arrived_at", train.currentStation());
                }
            }
            case DEPARTING -> {
                // running is the only state the station scan and the standstill relatch run from, so a car that never rolled got stuck
                train.setStationState(StationState.RUNNING);
            }
        }
    }

    public static boolean parkOnMark(SableTrain train, GlobalStation station) {
        if (train.isDerailed() || train.isPhysics() || Math.abs(train.speed()) > DEPART_SPEED) {
            return false;
        }
        StationTarget near = scanStation(train, STATION_PARK_RADIUS, station, STATION_PARK_RADIUS);
        if (near == null) {
            return false;
        }
        StationLanding.snapConsist(train, near.direction() * near.distance(), 0.0);
        AnalogControllerBlockEntity drive = SableTrainDriver.controllerIn(train);
        if (drive != null) {
            drive.setLocked(false);
            drive.setCurrentPower(0);
        }
        train.setCurrentStation(near.station());
        train.setDistanceToStation(0.0);
        train.setStationDirection(near.direction());
        train.setStationState(StationState.STOPPED);
        train.setAtStation(near.station());
        return true;
    }

    public static void updateStationPresence(SableTrain train) {
        if (train.atStation() == null) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        Car car = train.car();
        if (container == null || car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return;
        }
        Vector3dc p = sub.logicalPose().position();
        Vector3dc anchor = train.atStationPos();
        if (anchor == null) {
            train.setAtStationPos(new Vector3d(p));
            return;
        }
        double dx = p.x() - anchor.x();
        double dz = p.z() - anchor.z();
        if (dx * dx + dz * dz > LEFT_STATION_DISTANCE_SQR) {
            train.setAtStation(null);
        }
    }

    public static boolean isStationInRange(SableTrain train, GlobalStation station, double maxDistance,
                                           double frontOvershoot) {
        if (train == null || station == null || train.isDerailed()) {
            return false;
        }
        return scanStation(train, maxDistance, station, frontOvershoot) != null;
    }

    public static boolean sameStation(GlobalStation a, GlobalStation b) {
        return a == b || (a != null && b != null && a.id != null && a.id.equals(b.id));
    }

    static StationTarget scanStation(SableTrain train, double maxDistance, GlobalStation target) {
        return scanStation(train, maxDistance, target, 0.0);
    }

    static StationTarget scanStation(SableTrain train, double maxDistance, GlobalStation target,
                                             double frontOvershoot) {
        return scoutBothWays(noseRail(train), nosePoint(train), maxDistance, target, frontOvershoot);
    }

    // a rolling train only ever stops for what is ahead of its nose, a station it just passed is not a stop
    static StationTarget scanAhead(SableTrain train, double maxDistance, GlobalStation target) {
        int sense = Math.abs(train.speed()) > DEPART_SPEED ? ConsistNose.senseOf(train) : 0;
        if (sense == 0) {
            return scanStation(train, maxDistance, target);
        }
        return scoutDirection(noseRail(train), nosePoint(train), maxDistance, sense, target, 0.0);
    }

    private static boolean scheduleDrives(SableTrain car) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(car.id());
        return runtime != null && runtime.drivesTrain();
    }

    // lowest sub level id breaks ties, orderedchain walks a hashset so cars can disagree on the ends
    public static SableTrain stopOwner(SableTrain train) {
        ServerLevel level = train.level();
        UUID id = train.car() == null ? null : train.car().subLevelId();
        if (level == null || id == null) {
            return train;
        }
        if (ownersTick != stationTickCounter) {
            ownersTick = stationTickCounter;
            OWNERS.clear();
        }
        SableTrain known = OWNERS.get(id);
        if (known != null) {
            return known;
        }
        List<SableTrain> cars = new ArrayList<>();
        for (UUID other : TrainConsist.connectedSubLevels(level, id)) {
            SableTrain candidate = SableTrainRegistry.bySubLevel(other);
            if (candidate != null && candidate.car() != null && candidate.car().subLevelId() != null
                    && !candidate.isDerailed()) {
                cars.add(candidate);
            }
        }
        if (cars.isEmpty()) {
            return train;
        }
        List<OwnerCandidate> candidates = new ArrayList<>();
        for (SableTrain car : cars) {
            candidates.add(new OwnerCandidate(car, SableTrainDriver.operatedControllerIn(car) == null,
                    !scheduleDrives(car), SableTrainDriver.driverRank(car),
                    SableTrainDriver.controllerIn(car) == null));
        }
        candidates.sort(Comparator.comparing(OwnerCandidate::idle)
                .thenComparing(OwnerCandidate::unscheduled)
                .thenComparingInt(OwnerCandidate::rank)
                .thenComparing(OwnerCandidate::noCab)
                .thenComparing(candidate -> candidate.car().car().subLevelId()));
        SableTrain owner = candidates.get(0).car();
        for (SableTrain car : cars) {
            OWNERS.put(car.car().subLevelId(), owner);
        }
        return owner;
    }

    private record OwnerCandidate(SableTrain car, boolean idle, boolean unscheduled, int rank, boolean noCab) {}

    private static void mirrorStop(SableTrain train, SableTrain owner) {
        train.setStationState(owner.stationState());
        train.setSighted(owner.sightedStation(), owner.sightedDistance());
        train.setCurrentStation(owner.currentStation());
        train.setDistanceToStation(owner.distanceToStation());
        train.setStationDirection(owner.stationDirection());
        if (train.atStation() != owner.atStation()) {
            train.setAtStation(owner.atStation());
        }
    }

    public static TravellingPoint nosePoint(SableTrain train) {
        return ConsistNose.point(train);
    }

    public static RailCarriage noseRail(SableTrain train) {
        return ConsistNose.rail(train);
    }

    public static SableTrain.Bogey frontBogey(SableTrain train, java.util.List<SableTrain.Bogey> bogeys) {
        Vec3 nose = SableTrainDriver.axleNose(train);
        if (nose == null) {
            return bogeys.get(bogeys.size() - 1);
        }
        SableTrain.Bogey best = bogeys.get(0);
        double bestDot = -Double.MAX_VALUE;
        for (SableTrain.Bogey bogey : bogeys) {
            Vector3d center = bogey.rail() == null ? null : bogey.rail().center();
            if (center == null) {
                continue;
            }
            double dot = center.x * nose.x + center.y * nose.y + center.z * nose.z;
            if (dot > bestDot) {
                bestDot = dot;
                best = bogey;
            }
        }
        return best;
    }

    static StationTarget scoutBothWays(RailCarriage carriage, TravellingPoint origin, double maxDistance,
                                               GlobalStation target, double frontOvershoot) {
        if (origin == null || origin.edge == null) {
            return null;
        }
        return nearer(scoutDirection(carriage, origin, maxDistance, 1.0, target, frontOvershoot),
                scoutDirection(carriage, origin, maxDistance, -1.0, target, frontOvershoot));
    }

    static StationTarget nearer(StationTarget a, StationTarget b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.distance() <= b.distance() ? a : b;
    }

    public record ScanReport(GlobalStation station, double distance, int edgePointsSeen,
                             List<String> stationsSeen) {}

    static StationTarget scoutDirection(RailCarriage carriage, TravellingPoint origin,
                                                double maxDistance, double direction, GlobalStation target,
                                                double frontOvershoot) {
        if (carriage == null || carriage.graph() == null || origin == null || origin.edge == null) {
            return null;
        }
        ScanReport report = scout(carriage, origin, maxDistance, direction, target, frontOvershoot);
        if (report.station() == null) {
            return null;
        }
        return new StationTarget(report.station(), report.distance(), direction);
    }

    private static ScanReport scout(RailCarriage carriage, TravellingPoint origin, double maxDistance,
                                    double direction, GlobalStation target, double frontOvershoot) {
        TravellingPoint scout = new TravellingPoint(
                origin.node1, origin.node2, origin.edge, origin.position, origin.upsideDown);
        scout.travel(carriage.graph(), -direction * StationLanding.parkOffset,
                scout.steer(SteerDirection.NONE, carriage.upNormal()),
                scout.ignoreEdgePoints(), scout.ignoreTurns(), scout.ignorePortals());
        GlobalStation[] found = { null };
        double[] foundDistance = { 0.0 };
        int[] edgePoints = { 0 };
        List<String> seen = new ArrayList<>();
        TravellingPoint.IEdgePointListener listener = (distance, pair) -> {
            edgePoints[0]++;
            if (!(pair.getFirst() instanceof GlobalStation station)) {
                return false;
            }
            Couple<TrackNode> nodes = pair.getSecond();
            boolean approachable = station.canApproachFrom(nodes.getSecond());
            boolean matches = target == null || sameStation(station, target);
            seen.add(station.name + " @" + String.format(java.util.Locale.ROOT, "%.3f", distance)
                    + " canApproach=" + approachable + " matchesTarget=" + matches);
            if (!matches || (!approachable && Math.abs(distance) > frontOvershoot)) {
                return false;
            }
            found[0] = station;
            foundDistance[0] = Math.abs(distance);
            return true;
        };
        scout.travel(carriage.graph(), direction * maxDistance,
                scout.steer(SteerDirection.NONE, carriage.upNormal()),
                listener, scout.ignoreTurns(), scout.ignorePortals());
        return new ScanReport(found[0], foundDistance[0], edgePoints[0], seen);
    }

    static void sendApproachPrompt(ServerPlayer player, GlobalStation station) {
        if (station == null) {
            return;
        }
        Component name = Component.literal(station.name).withStyle(s -> s.withColor(STATION_NAME_COLOR));
        Component text = Component.translatable("loconautics.train.approach_station",
                Component.keybind("key.jump"), name);
        LoconauticsNetwork.sendStationPrompt(player, text, false);
    }

    static void sendApproachBar(ServerPlayer player, SableTrain train, double distance) {
        double total = train.approachTotal();
        if (total <= 0.0) {
            sendStationPrompt(player, "loconautics.train.approaching_station", train.currentStation());
            return;
        }
        double f = distance / total;
        int progress = (int) (Mth.clamp(1.0 - (1.0 - f) * (1.0 - f), 0.0, 1.0) * 30);
        int filledColor = Color.mixColors(ARRIVED_COLOR, APPROACH_COLOR, progress / 30.0f);
        int restColor = progress == 0 ? ARRIVED_COLOR : REMAINING_COLOR;
        MutableComponent bar = Component.literal("|".repeat(30 - progress))
                .withStyle(s -> s.withColor(filledColor))
                .append(Component.literal("|".repeat(progress)).withStyle(s -> s.withColor(restColor)));
        LoconauticsNetwork.sendStationPrompt(player, bar, true);
    }

    static void sendStationPrompt(ServerPlayer player, String key, GlobalStation station) {
        if (station == null) {
            return;
        }
        Component name = Component.literal(station.name).withStyle(s -> s.withColor(STATION_NAME_COLOR));
        LoconauticsNetwork.sendStationPrompt(player, Component.translatable(key, name), false);
    }

    private TrainStations() {
    }
}
