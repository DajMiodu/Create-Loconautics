package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.turntable.Turntable;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.destination.CoupleInstruction;
import com.lycoris.loconautics.content.schedule.destination.DestinationInstruction;
import com.lycoris.loconautics.content.schedule.destination.WaypointInstruction;
import com.lycoris.loconautics.content.schedule.destination.ScheduleInstruction;
import com.lycoris.loconautics.content.trainmail.PostalState;
import com.lycoris.loconautics.content.trainmail.PostalEntry;
import com.lycoris.loconautics.content.trainmail.PostalPlan;
import com.lycoris.loconautics.content.trainmail.SablePostalWorld;
import com.lycoris.loconautics.content.trainmail.TrainMailTransfer;
import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.trainphysics.signal.SignalSnap;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.drive.TrainBrakes;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class SableScheduleRuntime implements PostalState {

    private static final double ARRIVE_RADIUS = 3.0;
    private static final double ENTRY_CLEARANCE = 3.0;

    private static final double BRAKE_MIN_RANGE = 4.0;

    private static final double CHIME_ARM_RANGE = 100.0;

    private static final double CHIME_RANGE = 60.0;


    private static final int MAX_IDLE_TURNS = 3;
    private static final int MAX_LOST_MEASURES = 3;

    private static final int REPLAN_TICKS = 20;
    private static final int POSTAL_CHECK_TICKS = 10;

    private static final int COOLDOWN = 40;
    private static final int WAIT_POLL = 10;
    private static final int CONDITION_SAVE_TICKS = 20;
    private static final int LEVER_PROBE_SETTLE = 3;
    private static final int LEVER_PROBE_GIVE_UP = 60;
    private static final double LEVER_PROBE_MAX_SPEED = 0.01;

    private static final Map<UUID, SableScheduleRuntime> RUNTIMES = new ConcurrentHashMap<>();

    public enum State {
        PRE_TRANSIT,
        IN_TRANSIT,
        TURNTABLE,
        POST_TRANSIT
    }

    public Schedule schedule;
    public int currentEntry;
    public State state = State.PRE_TRANSIT;
    public boolean paused;
    public boolean completed;

    private final List<Integer> conditionProgress = new ArrayList<>();
    private final List<CompoundTag> conditionContext = new ArrayList<>();
    private final ScheduleAnnouncer announcer = new ScheduleAnnouncer(this);

    private SableTrain train;
    private AnalogControllerBlockEntity controller;
    private int powerLimit = -1;
    private SchedulePath path;
    private boolean parked;
    private int terminalSense;
    private int brakeSense;
    private boolean leftStartEdge;
    private boolean tailLanding;
    private boolean signalHeld;
    private boolean signalParked;
    private String lastJunction;
    private String lastStatus;
    private double lastRemaining = RouteDistance.UNKNOWN;
    private int replanCounter;
    private int postalCheckCounter;
    private boolean quietReplan;
    private int leverSign = 1;
    private boolean leverKnown;
    private int leverProbeTicks;
    private int maxNotches;

    private int cooldown;
    private boolean needsReplan;
    private boolean turntableAligning;
    private int idleTurns;
    private int lostMeasures;
    private boolean released;
    private String heldReason;

    private static final int NO_ROTATION_GRACE_TICKS = 40;
    private static final int NO_DRIVER_GRACE_TICKS = 300;
    private static final String NO_DRIVER = "no_driver";
    private int transientTicks;
    private String transientReason;

    private static int graceTicks(String reason) {
        return switch (reason) {
            case "no_rotation" -> NO_ROTATION_GRACE_TICKS;
            case NO_DRIVER -> NO_DRIVER_GRACE_TICKS;
            default -> 0;
        };
    }
    private UUID departedFrom;
    private boolean chimeArmed;
    private boolean postalTripped;
    private boolean postalRepeat;
    private boolean postalDeposit;
    private final Set<UUID> postalWarned = new HashSet<>();
    private boolean waitingHere;
    private int conditionSaveCounter;
    private boolean traceNow;
    private boolean maneuver;
    private int maneuverPercent = LoconauticsConstants.MANEUVER_DEFAULT_PERCENT;
    private final Set<UUID> former = new HashSet<>();
    private final Set<UUID> berthed = new HashSet<>();
    @Nullable
    private ManeuverPlan maneuverPlan;
    private boolean deadEndNotified;
    @Nullable
    private Component maneuverWhy;
    private boolean creeping;
    private boolean reversalUsed;
    @Nullable
    private ArrivalChime chime;

    public static SableScheduleRuntime assign(UUID trainId, Schedule schedule) {
        if (trainId == null || schedule == null || schedule.entries.isEmpty()) {
            return null;
        }
        SableScheduleRuntime runtime = new SableScheduleRuntime();
        runtime.schedule = schedule;
        runtime.currentEntry = Mth.clamp(schedule.savedProgress, 0, schedule.entries.size() - 1);
        runtime.state = State.PRE_TRANSIT;
        RUNTIMES.put(trainId, runtime);
        SableTrain train = SableTrainRegistry.get(trainId);
        if (train != null) {
            TrainStatusChat.newSchedule(train);
        }
        return runtime;
    }

    @Nullable
    public static SableScheduleRuntime get(UUID trainId) {
        return RUNTIMES.get(trainId);
    }

    public static void clear(UUID trainId) {
        RUNTIMES.remove(trainId);
        ConsistNose.forget(SableTrainRegistry.get(trainId));
    }

    // the asking car first, then the rest by id, so every lookup walks a consist in the same order
    public static List<SableTrain> consistCars(SableTrain car) {
        List<SableTrain> mates = new ArrayList<>();
        for (UUID subLevelId : car.consistCache()) {
            SableTrain mate = SableTrainRegistry.bySubLevel(subLevelId);
            if (mate != null && mate != car) {
                mates.add(mate);
            }
        }
        mates.sort(Comparator.comparing(SableTrain::id));
        mates.add(0, car);
        return mates;
    }

    // one consist runs one schedule, kept under whichever car it drives from right now
    @Nullable
    public static UUID consistKey(SableTrain car) {
        List<SableTrain> cars = consistCars(car);
        for (SableTrain mate : cars) {
            if (RUNTIMES.containsKey(mate.id())) {
                return mate.id();
            }
        }
        ScheduleProgressStore store = storeOf(car);
        if (store == null) {
            return null;
        }
        for (SableTrain mate : cars) {
            if (store.hasSchedule(mate.id())) {
                return mate.id();
            }
        }
        return null;
    }

    public static UUID consistKey(UUID trainId) {
        SableTrain car = SableTrainRegistry.get(trainId);
        UUID key = car == null ? null : consistKey(car);
        return key == null ? trainId : key;
    }

    @Nullable
    public static SableScheduleRuntime forConsist(SableTrain car) {
        for (SableTrain mate : consistCars(car)) {
            SableScheduleRuntime runtime = RUNTIMES.get(mate.id());
            if (runtime != null) {
                return runtime;
            }
        }
        return null;
    }

    @Nullable
    private static ScheduleProgressStore storeOf(SableTrain car) {
        return car.level() == null || car.level().getServer() == null
                ? null : ScheduleProgressStore.get(car.level().getServer());
    }

    public boolean heldAtSignal() {
        return signalHeld && drivesTrain();
    }

    public boolean drivesTrain() {
        return !paused && !completed && schedule != null;
    }

    public boolean maneuver() {
        return maneuver;
    }

    public Set<UUID> former() {
        return Collections.unmodifiableSet(former);
    }

    // wagons the loco may roll up to, the ones it left plus a train parked at the stop it is berthing behind
    public Set<UUID> maneuverFriends() {
        Set<UUID> friends = new HashSet<>(former);
        friends.addAll(berthed);
        return friends;
    }

    void recordBerth(@Nullable SableTrain parked) {
        berthed.clear();
        if (parked != null) {
            berthed.add(parked.id());
        }
    }

    public void startManeuver(Set<UUID> former, int percent) {
        maneuver = true;
        maneuverPercent = Math.clamp(percent, 1, LoconauticsConstants.MANEUVER_MAX_PERCENT);
        Set<UUID> kept = former == null ? Set.of() : Set.copyOf(former);
        this.former.clear();
        this.former.addAll(kept);
        maneuverPlan = null;
        deadEndNotified = false;
        reversalUsed = false;
        LoconauticsConstants.LOGGER.info("[maneuver] {} start, {} cars left behind", trainTag(), this.former.size());
        announcer.line(Component.literal("Maneuver started, " + this.former.size() + " cars left behind"));
        if (train != null) {
            TrainStatusChat.maneuverStarted(train);
        }
        saveManeuver();
    }

    public void endManeuver() {
        if (maneuver) {
            LoconauticsConstants.LOGGER.info("[maneuver] {} end, {} cars still left behind", trainTag(), former.size());
            announcer.line(Component.literal("Maneuver done"));
            if (train != null) {
                TrainStatusChat.maneuverEnded(train);
            }
        }
        maneuver = false;
        maneuverPlan = null;
        maneuverWhy = null;
        berthed.clear();
        deadEndNotified = false;
        reversalUsed = false;
        saveManeuver();
    }

    public void clearFormer() {
        former.clear();
        saveManeuver();
    }

    public void rememberFormer(Set<UUID> ids) {
        if (ids != null) {
            former.addAll(ids);
        }
        saveManeuver();
    }

    private void saveManeuver() {
        if (train != null && train.level() != null && schedule != null) {
            sync();
        }
    }

    private String trainTag() {
        return train == null ? "none" : shortId(train.id());
    }

    private void traceTurn(String stage, TurntableRegistry.Exit hop, Turntable turntable) {
        if (train == null || train.level() == null
                || !TurntableTrace.due(trainTag() + stage, train.level().getGameTime(), 20)) {
            return;
        }
        UUID plate = hop == null ? null : hop.plate();
        TrackHostFrames.Frame frame = TrackHostFrames.frameOf(plate);
        double error = turntable == null || hop == null ? Double.NaN
                : TurntableRegistry.alignmentError(turntable, hop.movingHead(), hop.worldHead());
        TurntableTrace.log(trainTag() + " " + stage + " ghost=" + train.isGhost() + " speed="
                + String.format("%.3f", train.speed()) + " impl="
                + (turntable == null ? "none" : turntable.getClass().getSimpleName()) + " powered="
                + (turntable != null && turntable.turntablePowered()) + " sched="
                + (turntable != null && turntable.turntableScheduleDriven()) + " aligning=" + turntableAligning
                + " aligned=" + (turntable != null && turntable.turntableAligned()) + " settled="
                + (turntable != null && turntable.turntableSettled()) + " error=" + String.format("%.2f", error)
                + " frameAngle=" + (frame == null ? "none" : String.format("%.2f", frame.angle()))
                + " frameLoaded=" + (frame != null && frame.loaded()) + " hostMissing="
                + (train.car() != null && train.car().carriage().railHostMissing()) + " reserved="
                + TurntableRegistry.reserved(plate) + " occupied=" + TurntableRegistry.occupied(plate)
                + " entry=" + (hop != null && hop.entry()) + " plate=" + (plate == null ? "none" : shortId(plate)));
    }

    int plannedRel() {
        return maneuverPlan == null ? 0 : maneuverPlan.currentRel();
    }

    void maneuverPlanFailed(String why) {
        LoconauticsConstants.LOGGER.info("[maneuver] {} stored plan {} found no route, planning again, {}",
                trainTag(), maneuverPlan, why);
        maneuverPlan = null;
    }

    void recordManeuverLeg(SchedulePath best, int rel) {
        if (maneuverPlan == null) {
            maneuverPlan = new ManeuverPlan();
        }
        maneuverPlan.record(best.hop(), rel);
        TurntableRegistry.Exit hop = best.hop();
        String exit = hop == null ? "no plate" : hop.entry() ? "plate entry, leg ends at the approach end"
                : ManeuverPlan.backsOut(hop) ? "plate exit, backs out the way it came in"
                : "plate exit, spins and leaves " + (rel > 0 ? "cab" : "tail") + " end first";
        LoconauticsConstants.LOGGER.info("[maneuver] {} plan legs {} leg sense {} dir {} {}{}",
                trainTag(), maneuverPlan, rel > 0 ? "cab first" : "backing", best.direction(), exit,
                hop == null ? "" : ", heads entry " + hop.entryHead() + " moving " + hop.movingHead()
                        + " world " + hop.worldHead());
    }

    boolean reversalAllowed() {
        return maneuver && !reversalUsed;
    }

    void recordReversalLeg(int rel) {
        if (maneuverPlan == null) {
            maneuverPlan = new ManeuverPlan();
        }
        maneuverPlan.recordReversal(rel);
        LoconauticsConstants.LOGGER.info("[maneuver] {} plan legs {} leg one {} then stop and reverse", trainTag(),
                maneuverPlan, rel > 0 ? "cab first" : "backing");
    }

    // a path with no station and no plate is a stop point where the train turns around
    private boolean reversalStop() {
        return maneuver && path != null && path.station() == null && path.hop() == null;
    }

    private void turnAround(AnalogControllerBlockEntity controller) {
        command(controller, 0);
        train.setTargetSpeed(0.0);
        train.setScheduleHold(true);
        clearRoute();
        parked = false;
        reversalUsed = true;
        LoconauticsConstants.LOGGER.info("[maneuver] {} stopped at the reversal point, going back", trainTag());
        traceCycle("turnAround");
        announcer.line(Component.literal("Stopped clear of the switch, reversing"));
        advanceManeuver();
        terminalSense = 0;
        brakeSense = 0;
        lastRemaining = RouteDistance.UNKNOWN;
        quietReplan = true;
        needsReplan = true;
        state = State.PRE_TRANSIT;
        startCooldown();
    }

    private void advanceManeuver() {
        if (maneuver && maneuverPlan != null) {
            maneuverPlan.advance();
            LoconauticsConstants.LOGGER.info("[maneuver] {} plate leg done, plan now {}", trainTag(), maneuverPlan);
        }
    }

    private double maneuverScale() {
        return maneuver ? maneuverPercent / 100.0 : 1.0;
    }

    private double cappedSpeed(double mag) {
        return maneuver ? Math.min(mag, TrainPropulsion.gearedTopBpt(train) * maneuverScale()) : mag;
    }

    // the plain reason the last plan found no way, told once with the dead end line
    void explainManeuver(@Nullable Component why) {
        maneuverWhy = why;
    }

    private void maneuverDeadEnd(AnalogControllerBlockEntity controller) {
        train.setTargetSpeed(0.0);
        // a hold full brakes the whole rake
        train.setScheduleHold(false);
        command(controller, 0);
        maneuverPlan = null;
        cooldown = LoconauticsConstants.MANEUVER_DEAD_END_RETRY_TICKS;
        String name = currentInstruction() instanceof DestinationInstruction destination
                ? destination.getFilter() : "";
        LoconauticsConstants.LOGGER.info("[maneuver] {} dead end to \"{}\", retry in {} ticks, told={}",
                trainTag(), name, cooldown, deadEndNotified);
        if (!deadEndNotified) {
            deadEndNotified = true;
            TrainStatusChat.maneuverDeadEnd(train, name, maneuverWhy);
        }
    }

    @Nullable
    public static SableTrain headingTo(GlobalStation station, SableTrain asking) {
        for (SableScheduleRuntime runtime : RUNTIMES.values()) {
            if (runtime.train == null || runtime.train == asking || runtime.paused
                    || runtime.state != State.IN_TRANSIT || runtime.path == null) {
                continue;
            }
            if (TrainStations.sameStation(station, runtime.path.station())) {
                return runtime.train;
            }
        }
        return null;
    }

    @EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
    public static final class Lifecycle {
        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            RUNTIMES.clear();
            ConsistNose.clearAll();
        }
    }

    public static boolean resume(UUID trainId) {
        SableScheduleRuntime runtime = RUNTIMES.get(consistKey(trainId));
        if (runtime == null || !runtime.paused || runtime.completed) {
            return false;
        }
        runtime.paused = false;
        runtime.departedFrom = null;
        runtime.state = State.PRE_TRANSIT;
        return true;
    }

    public SableTrain train() {
        return train;
    }

    public RailCarriage leadRail() {
        List<SableTrain.Bogey> bogeys = train.car().bogeys();
        return bogeys.isEmpty() ? train.car().carriage() : bogeys.get(bogeys.size() - 1).rail();
    }

    private void setRoute(List<Couple<TrackNode>> route) {
        List<Couple<TrackNode>> shared = route == null ? null : new java.util.ArrayList<>(route);
        List<SableTrain.Bogey> bogeys = train.car().bogeys();
        if (train.isTrackless()) {
            return;
        }
        if (bogeys.isEmpty()) {
            train.car().carriage().setPath(shared);
            return;
        }
        for (SableTrain.Bogey bogey : bogeys) {
            bogey.rail().setPath(shared);
        }
        routeCarsAhead(route);
    }

    // reversing, the cars in front lead the switch and the loco only pushes, so they need the route too
    private void routeCarsAhead(List<Couple<TrackNode>> route) {
        if (maneuver) {
            for (UUID id : TrainConsist.orderedChain(train.level(), train.car().subLevelId())) {
                SableTrain other = SableTrainRegistry.bySubLevel(id);
                if (other == null || other == train || other.car() == null || other.isTrackless()) {
                    continue;
                }
                List<Couple<TrackNode>> copy = route == null ? null : new ArrayList<>(route);
                if (other.car().bogeys().isEmpty()) {
                    other.car().carriage().setPath(copy);
                    continue;
                }
                for (SableTrain.Bogey bogey : other.car().bogeys()) {
                    bogey.rail().setPath(copy);
                }
            }
        }
    }

    private void clearRoute() {
        setRoute(null);
    }

    // backing in, the trailing end takes the mark so the rake ends up where a forward arrival puts it
    private ConsistNose.Nose stopEnd() {
        return tailLanding ? ConsistNose.tail(train) : ConsistNose.of(train);
    }

    private TravellingPoint referencePoint() {
        return pointOf(stopEnd());
    }

    private TravellingPoint pointOf(ConsistNose.Nose end) {
        TravellingPoint nose = end.point();
        return nose == null || nose.edge == null ? leadRail().leading() : nose;
    }

    private RouteDistance.Measure measure(TrackGraphLocation loc) {
        return measureFrom(stopEnd(), loc);
    }

    private RouteDistance.Measure measureFrom(ConsistNose.Nose end, TrackGraphLocation loc) {
        if (path == null) {
            return RouteDistance.Measure.unknown("no path");
        }
        TravellingPoint point = pointOf(end);
        RailCarriage rail = end.rail();
        TrackGraph graph = RailSeams.liveGraphOf(point, rail == null ? leadRail().graph() : rail.graph());
        return RouteDistance.measure(point, graph, path.route(), loc);
    }

    private double measureRemaining(TrackGraphLocation loc) {
        return measure(loc).distance();
    }

    private boolean onTargetEdge(TrackGraphLocation loc) {
        return RouteDistance.onTargetEdge(referencePoint(), loc);
    }

    private double signedTerminalOffset(TrackGraphLocation loc) {
        return RouteDistance.signedOnEdge(referencePoint(), loc);
    }

    public void startCooldown() {
        cooldown = COOLDOWN;
    }

    public void waitHere() {
        waitingHere = true;
        cooldown = WAIT_POLL;
    }

    private int wagonDirection() {
        Vec3 tangent = ConsistNose.noseTangent(train);
        Vector3d here = TrainStatusChat.bodyPosition(train);
        if (tangent == null || here == null) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        int sense = 0;
        for (UUID id : former) {
            SableTrain wagon = SableTrainRegistry.get(id);
            Vector3d there = wagon == null ? null : TrainStatusChat.bodyPosition(wagon);
            if (there == null || here.distanceSquared(there) >= best) {
                continue;
            }
            best = here.distanceSquared(there);
            sense = (there.x - here.x) * tangent.x + (there.z - here.z) * tangent.z >= 0.0 ? 1 : -1;
        }
        return sense * ConsistNose.frameFlip(train);
    }

    private void holdWhileWaiting(AnalogControllerBlockEntity controller) {
        int toWagons = maneuver && currentInstruction() instanceof CoupleInstruction ? wagonDirection() : 0;
        if (toWagons != 0) {
            creeping = true;
            released = false;
            train.setScheduleHold(false);
            LoconauticsConstants.LOGGER.info("[maneuver] {} creeping toward the wagons, body direction {}",
                    trainTag(), toWagons);
            drive(controller, toWagons);
            return;
        }
        released = false;
        train.setScheduleHold(true);
        train.setTargetSpeed(0.0);
        command(controller, 0);
    }

    @Override
    public boolean loconautics$tripped() {
        return postalTripped;
    }

    @Override
    public void loconautics$setTripped(boolean tripped) {
        postalTripped = tripped;
        if (!tripped) {
            postalWarned.clear();
        }
    }

    @Override
    public void loconautics$setRepeat(boolean repeat) {
        postalRepeat = repeat;
    }

    @Override
    public Set<UUID> loconautics$warned() {
        return postalWarned;
    }

    @Override
    public boolean loconautics$repeat() {
        return postalRepeat;
    }

    @Override
    public boolean loconautics$deposit() {
        return postalDeposit;
    }

    @Override
    public void loconautics$setDeposit(boolean deposit) {
        postalDeposit = deposit;
    }

    @Nullable
    public ScheduleInstruction currentInstruction() {
        if (schedule == null || currentEntry < 0 || currentEntry >= schedule.entries.size()) {
            return null;
        }
        return schedule.entries.get(currentEntry).instruction;
    }

    public void setPowerLimit(int power) {
        powerLimit = power;
    }

    public int maxPower() {
        return controller == null ? maxNotches : controller.getMaxPower();
    }

    private int currentPower() {
        return controller == null ? 0 : controller.getCurrentPower();
    }

    public void steer(int dir) {
        if (controller != null) {
            controller.setScheduleSteer(dir);
        }
    }

    @Nullable
    public String heldReason() {
        return heldReason;
    }

    @Nullable
    public GlobalStation targetStation() {
        return path == null ? null : path.station();
    }

    public double remainingToTarget() {
        return lastRemaining;
    }

    public boolean justDeparted(GlobalStation station) {
        return departedFrom != null && station != null && departedFrom.equals(station.id);
    }

    public CompoundTag saveProgress() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", currentEntry);
        tag.putString("Phase", state.name());
        tag.putBoolean("Running", !paused);
        tag.putBoolean("Completed", completed);
        tag.putInt("Limit", powerLimit);
        tag.putInt("LeverSign", leverKnown ? leverSign : 0);
        if (departedFrom != null) {
            tag.putUUID("DepartedFrom", departedFrom);
        }
        tag.putBoolean("Maneuver", maneuver);
        tag.putInt("ManeuverPercent", maneuverPercent);
        if (!former.isEmpty()) {
            ListTag ids = new ListTag();
            for (UUID id : former) {
                ids.add(NbtUtils.createUUID(id));
            }
            tag.put("Former", ids);
        }
        if (state == State.POST_TRANSIT && !conditionProgress.isEmpty()) {
            tag.putIntArray("ConditionProgress", conditionProgress);
            ListTag contexts = new ListTag();
            for (CompoundTag context : conditionContext) {
                contexts.add(context.copy());
            }
            tag.put("ConditionContext", contexts);
        }
        return tag;
    }

    public void restoreProgress(CompoundTag tag) {
        if (tag == null || schedule == null || schedule.entries.isEmpty()) {
            return;
        }
        currentEntry = Math.max(0, Math.min(schedule.entries.size() - 1, tag.getInt("Index")));
        state = phaseOf(tag.getString("Phase"));
        paused = tag.contains("Running") && !tag.getBoolean("Running");
        completed = tag.getBoolean("Completed");
        powerLimit = tag.contains("Limit") ? tag.getInt("Limit") : -1;
        int savedLever = tag.getInt("LeverSign");
        if (savedLever != 0) {
            leverSign = savedLever;
            leverKnown = true;
        }
        departedFrom = tag.hasUUID("DepartedFrom") ? tag.getUUID("DepartedFrom") : null;
        maneuver = tag.getBoolean("Maneuver");
        maneuverPercent = tag.contains("ManeuverPercent")
                ? tag.getInt("ManeuverPercent") : LoconauticsConstants.MANEUVER_DEFAULT_PERCENT;
        former.clear();
        for (Tag id : tag.getList("Former", Tag.TAG_INT_ARRAY)) {
            former.add(NbtUtils.loadUUID(id));
        }
        if (state == State.IN_TRANSIT || state == State.TURNTABLE) {
            state = State.PRE_TRANSIT;
        }
        if (state == State.POST_TRANSIT) {
            boolean loaded = loadConditions(tag);
            if (!loaded) {
                resetConditions();
            }
            ScheduleGhostTrace.event(null, "restored phase POST_TRANSIT entry=" + currentEntry
                    + " conditionsLoaded=" + loaded + " " + conditionText());
        }
    }

    // a quit while parked keeps how far each wait got, the way create saves its own condition progress
    private boolean loadConditions(CompoundTag tag) {
        List<List<ScheduleWaitCondition>> columns = schedule.entries.get(currentEntry).conditions;
        int[] saved = tag.getIntArray("ConditionProgress");
        ListTag contexts = tag.getList("ConditionContext", Tag.TAG_COMPOUND);
        if (saved.length != columns.size() || contexts.size() != columns.size()) {
            return false;
        }
        conditionProgress.clear();
        conditionContext.clear();
        for (int i = 0; i < saved.length; i++) {
            conditionProgress.add(Mth.clamp(saved[i], 0, columns.get(i).size()));
            conditionContext.add(contexts.getCompound(i));
        }
        return true;
    }

    private void resetConditions() {
        conditionProgress.clear();
        conditionContext.clear();
        for (List<ScheduleWaitCondition> ignored : schedule.entries.get(currentEntry).conditions) {
            conditionProgress.add(0);
            conditionContext.add(new CompoundTag());
        }
    }

    private static State phaseOf(String name) {
        for (State phase : State.values()) {
            if (phase.name().equals(name)) {
                return phase;
            }
        }
        return State.PRE_TRANSIT;
    }

    public static void tick(SableTrain train, AnalogControllerBlockEntity controller) {
        if (train.isTrackless()) {
            return;
        }
        SableScheduleRuntime runtime = RUNTIMES.get(train.id());
        if (runtime == null) {
            runtime = rehydrate(train);
        }
        boolean keeps = runtime != null && keepsConsist(train);
        issue301(train, controller, runtime != null, keeps);
        if (keeps) {
            runtime.tickSelf(train, controller);
            return;
        }
        if (controller != null && controller.isScheduleControlled()) {
            LoconauticsConstants.LOGGER.info("[schedule] {} no runtime, releasing controller", shortId(train.id()));
            issue301(train, controller, runtime != null, false);
            controller.clearScheduleControl();
        }
    }

    private static final Map<UUID, Long> ISSUE301_LAST = new HashMap<>();

    private static void issue301(SableTrain train, AnalogControllerBlockEntity controller, boolean hasRuntime,
                                 boolean keeps) {
        boolean live = Math.abs(train.targetSpeed()) > 1.0e-6 || Math.abs(train.speed()) > 1.0e-3
                || controller != null && (controller.getCurrentPower() != 0 || controller.getSteerInput() != 0);
        if (train.level() == null) {
            return;
        }
        long now = train.level().getGameTime();
        Long last = ISSUE301_LAST.get(train.id());
        if (!live || last != null && now - last < 40L) {
            return;
        }
        ISSUE301_LAST.put(train.id(), now);
        LoconauticsConstants.LOGGER.info("[issue301] {} runtime={} keeps={} residency={} ctrl={} power={} locked={} user={} "
                        + "schedCtrl={} steer={} speed={} target={} powered={} schedDriven={} schedHold={} station={}",
                shortId(train.id()), hasRuntime, keeps, train.residency(), controller != null,
                controller == null ? "none" : controller.getCurrentPower(),
                controller != null && controller.isLocked(), controller != null && controller.hasUser(),
                controller != null && controller.isScheduleControlled(),
                controller == null ? "none" : controller.getSteerInput(), String.format("%.3f", train.speed()),
                String.format("%.3f", train.targetSpeed()), train.isPowered(), train.isScheduleDriven(),
                train.scheduleHold(), train.stationState());
    }

    @Nullable
    private static SableScheduleRuntime rehydrate(SableTrain train) {
        if (train.level() == null || train.level().getServer() == null) {
            return null;
        }
        ScheduleProgressStore store = ScheduleProgressStore.get(train.level().getServer());
        CompoundTag tag = store.getSchedule(train.id());
        if (tag == null) {
            return null;
        }
        SableScheduleRuntime runtime = assign(train.id(),
                Schedule.fromTag(train.level().registryAccess(), tag));
        if (runtime != null) {
            runtime.restoreProgress(store.getProgress(train.id()));
        }
        return runtime;
    }

    // a coupling or an old save can leave two schedules on one consist
    private static boolean keepsConsist(SableTrain train) {
        List<SableTrain> holders = new ArrayList<>();
        boolean allLoaded = true;
        for (SableTrain mate : consistCars(train)) {
            if (RUNTIMES.containsKey(mate.id())) {
                holders.add(mate);
                allLoaded &= subLevelOf(mate) != null;
            }
        }
        if (holders.size() < 2) {
            return true;
        }
        SableTrain owner = allLoaded ? TrainStations.stopOwner(train) : null;
        SableTrain keep = holders.contains(owner) ? owner
                : holders.stream().min(Comparator.comparing(SableTrain::id)).orElse(train);
        for (SableTrain holder : holders) {
            // an unloaded loser is only skipped, its item can be dropped once its body is back
            if (holder != keep && subLevelOf(holder) != null) {
                dropDuplicate(holder, keep);
            }
        }
        return keep == train;
    }

    private static void dropDuplicate(SableTrain holder, SableTrain keep) {
        SableScheduleRuntime dropped = RUNTIMES.remove(holder.id());
        ScheduleProgressStore store = storeOf(holder);
        CompoundTag tag = store == null ? null : store.getSchedule(holder.id());
        if (tag == null && dropped.schedule != null) {
            tag = dropped.schedule.write(holder.level().registryAccess());
        }
        if (tag != null) {
            tag.putInt("Progress", dropped.currentEntry);
        }
        if (store != null) {
            store.remove(holder.id());
        }
        if (dropped.train != null) {
            dropped.release();
        }
        ServerSubLevel sub = subLevelOf(holder);
        BlockPos seat = ScheduleReadiness.driverSeat(sub, holder.id());
        ScheduleConductor.dropSchedule(sub.getLevel(), seat != null ? seat : sub.getPlot().getCenterBlock(), tag);
        LoconauticsConstants.LOGGER.info("[schedshare] consist had two schedules, kept {} dropped {} as an item at {}",
                shortId(keep.id()), shortId(holder.id()), seat);
    }

    private void tickSelf(SableTrain train, AnalogControllerBlockEntity controller) {
        this.train = train;
        this.controller = controller;
        traceNow = state == State.POST_TRANSIT && ScheduleGhostTrace.due(train);
        if (traceNow) {
            ScheduleGhostTrace.log(train, stateText());
        }
        if (chime != null && !chime.tick()) {
            chime = null;
        }
        if (paused || completed || schedule == null || schedule.entries.isEmpty()) {
            if (controller != null && controller.isScheduleControlled()) {
                controller.clearScheduleControl();
            }
            clearRoute();
            return;
        }

        if (controller != null) {
            maxNotches = controller.getMaxPower();
        }
        if (playerAtControls(train)) {
            paused = true;
            if (controller != null) {
                controller.clearScheduleControl();
            }
            train.setScheduleHold(false);
            clearRoute();
            ConsistNose.forget(train);
            TrainStatusChat.manualControls(train);
            sync();
            return;
        }
        if (train.isDerailed() || train.isPhysics()) {
            command(controller, 0);
            if (state == State.IN_TRANSIT) {
                quietReplan = true;
                state = State.PRE_TRANSIT;
            }
            return;
        }

        if (!train.isGhost()) {
            ScheduleReadiness.Result ready = ScheduleReadiness.check(subLevelOf(train), controller, train.id());
            if (!ready.ok() && NO_DRIVER.equals(ready.reasonKey()) && handOff()) {
                return;
            }
            int grace = ready.ok() ? 0 : graceTicks(ready.reasonKey());
            transientTicks = grace > 0 && ready.reasonKey().equals(transientReason) ? transientTicks + 1 : 0;
            transientReason = grace > 0 ? ready.reasonKey() : null;
            if (!ready.ok() && (grace == 0 || transientTicks >= grace)) {
                hold(controller, ready.reasonKey());
                return;
            }
            if (grace > 0) {
                train.setTargetSpeed(0.0);
                return;
            }
        }
        if (heldReason != null) {
            heldReason = null;
            train.setScheduleHold(false);
            TrainStatusChat.successfulNavigation(train);
        }
        if (cooldown > 0) {
            cooldown--;
            if (!released && controller != null && !creeping) {
                controller.setScheduleTargetPower(0);
            }
            return;
        }

        switch (state) {
            case PRE_TRANSIT -> tickPreTransit(controller);
            case IN_TRANSIT -> tickInTransit(controller);
            case TURNTABLE -> tickTurntable(controller);
            case POST_TRANSIT -> tickPostTransit(controller);
        }
    }

    private static boolean playerAtControls(SableTrain host) {
        for (SableTrain mate : consistCars(host)) {
            ServerSubLevel sub = subLevelOf(mate);
            if (sub == null) {
                continue;
            }
            for (AnalogControllerBlockEntity cab : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
                if (cab.hasUser()) {
                    return true;
                }
            }
        }
        return false;
    }

    // the cab this ran from lost its conductor, another cab of the consist that still has one carries on
    private boolean handOff() {
        SableTrain next = drivableMate(train);
        if (next == null || !RUNTIMES.remove(train.id(), this)) {
            return false;
        }
        RUNTIMES.put(next.id(), this);
        ScheduleProgressStore store = storeOf(train);
        if (store != null) {
            store.move(train.id(), next.id());
        }
        LoconauticsConstants.LOGGER.info("[schedshare] schedule moved from {} to {} in state {}",
                shortId(train.id()), shortId(next.id()), state);
        if (controller != null) {
            controller.clearScheduleControl();
        }
        clearRoute();
        ConsistNose.forget(train);
        next.setScheduleHold(train.scheduleHold());
        train.setScheduleHold(false);
        leverSign = 1;
        leverKnown = false;
        leverProbeTicks = 0;
        transientTicks = 0;
        transientReason = null;
        if (state == State.IN_TRANSIT || state == State.TURNTABLE) {
            resumeAfterTurn();
        }
        return true;
    }

    @Nullable
    private static SableTrain drivableMate(SableTrain host) {
        SableTrain fallback = null;
        for (SableTrain mate : consistCars(host)) {
            ServerSubLevel sub = mate == host ? null : subLevelOf(mate);
            AnalogControllerBlockEntity cab = ScheduleReadiness.drivenController(sub, mate.id());
            if (cab == null) {
                continue;
            }
            if (ScheduleReadiness.check(sub, cab, mate.id()).ok()) {
                return mate;
            }
            if (fallback == null) {
                fallback = mate;
            }
        }
        return fallback;
    }

    private void hold(AnalogControllerBlockEntity controller, String reason) {
        if (controller != null) {
            controller.setScheduleTargetPower(0);
        }
        train.setTargetSpeed(0.0);
        train.setScheduleHold(true);
        needsReplan = true;
        if (!reason.equals(heldReason)) {
            heldReason = reason;
            LoconauticsConstants.LOGGER.info("[schedule] {} holding, {}", shortId(train.id()), reason);
            TrainStatusChat.cannotContinue(train,
                    LoconauticsLang.translate("schedule.hold." + reason).component());
        }
    }

    private void release() {
        released = true;
        signalHeld = false;
        signalParked = false;
        if (controller != null) {
            controller.clearScheduleControl();
        }
        train.setScheduleHold(false);
        clearRoute();
        ConsistNose.forget(train);
        terminalSense = 0;
        lastRemaining = RouteDistance.UNKNOWN;
        lastJunction = null;
        parked = false;
    }

    private void tickPreTransit(AnalogControllerBlockEntity controller) {
        ScheduleEntry entry = schedule.entries.get(currentEntry);
        ScheduleInstruction instruction = entry.instruction;
        boolean quiet = quietReplan;
        quietReplan = false;

        if (probeLeverSign(controller)) {
            return;
        }
        if (creeping) {
            creeping = false;
            command(controller, 0);
            train.setTargetSpeed(0.0);
        }
        announcer.reset();
        parked = false;
        int startedOn = currentEntry;
        ConsistNose.pinCab(train);
        path = instruction.start(this, train.level());
        if (path != null && noseAgainstLanding(instruction)) {
            path = null;
        }
        needsReplan = false;
        // a waiting step keeps its hold, letting go for one tick brakes the whole consist
        if (path != null || !waitingHere) {
            train.setScheduleHold(false);
        }

        if (path == null) {
            if (currentEntry != startedOn) {
                wrapOrFinish(controller);
                return;
            }
            if (waitingHere) {
                waitingHere = false;
                holdWhileWaiting(controller);
                return;
            }
            if (maneuver && instruction instanceof DestinationInstruction) {
                maneuverDeadEnd(controller);
                return;
            }
            release();
            return;
        }
        if (deadEndNotified) {
            LoconauticsConstants.LOGGER.info("[maneuver] {} dead end cleared, a path appeared", trainTag());
            deadEndNotified = false;
        }
        released = false;

        if (path.followsRoute()) {
            setRoute(path.route());
        }
        leftStartEdge = path.route() != null && path.route().isEmpty();
        terminalSense = 0;
        brakeSense = 0;
        signalHeld = false;
        signalParked = false;
        lastRemaining = RouteDistance.UNKNOWN;
        lastJunction = null;
        orientNose();
        startHopLeg();
        state = State.IN_TRANSIT;
        if (!quiet) {
            chimeArmed = false;
            announcer.departing(instruction);
        }
    }

    // last guard for a plan that slipped through, a one cab train never parks with its nose against the station facing
    private boolean noseAgainstLanding(ScheduleInstruction instruction) {
        if (!maneuver || path.hop() != null || path.station() == null
                || !(instruction instanceof DestinationInstruction destination) || !destination.landsFront()
                || StationRouting.twoCabs(train)
                || path.route() != null && path.route().isEmpty() && justDeparted(path.station())) {
            return false;
        }
        int sense = SableTrainDriver.noseSense(train, TrainStations.nosePoint(train));
        int rel = sense == path.direction() ? 1 : -1;
        Boolean along = StationRouting.travelAlongFacing(train, path);
        boolean against = along != null && along != (rel > 0);
        LoconauticsConstants.LOGGER.info("[maneuver] {} landing check at {}: leg direction {} cab end looks sense {} so the cab "
                + "end {}, travel along the facing {}, nose ends against it {}", trainTag(), path.station().name,
                path.direction(), sense, rel > 0 ? "leads" : "trails", along == null ? "unknown" : along, against);
        if (!against) {
            return false;
        }
        explainManeuver(new StationRouting.Cause(StationRouting.Why.NOSE_AGAINST, path.station().name, "").component());
        noPath(destination.getFilter(), "the nose would end against the station facing", true);
        recordBerth(null);
        return true;
    }

    // the nose is the end that leads, the far end when backing
    private void orientNose() {
        tailLanding = maneuver && plannedRel() < 0 && path.hop() == null && path.station() != null && berthed.isEmpty();
        Vec3 forward = train.isTrackless() ? null : ConsistNose.noseTangent(train);
        if (forward == null) {
            return;
        }
        ConsistNose.pinTravel(train, forward.scale(path.direction()));
        LoconauticsConstants.LOGGER.info("[nose2c] schedule {} leg direction {} nose car {} sense now {} flip {}",
                shortId(train.id()), path.direction(), shortId(ConsistNose.of(train).car().id()),
                ConsistNose.senseOf(train), ConsistNose.frameFlip(train));
        traceFacing("leg start");
        LoconauticsConstants.LOGGER.info("[maneuver] {} leg lands on the {} end, nose car {} tail car {}",
                trainTag(), tailLanding ? "trailing" : "leading", shortId(ConsistNose.of(train).car().id()),
                shortId(ConsistNose.tail(train).car().id()));
    }

    private static String dirText(@Nullable Vec3 dir) {
        return dir == null ? "null" : String.format("(%.2f,%.2f,%.2f)", dir.x, dir.y, dir.z);
    }

    // temporary, the world facing of both ends and the signs the plate legs depend on
    private void traceFacing(String when) {
        if (!maneuver || train.isTrackless()) {
            return;
        }
        ConsistNose.Nose lead = ConsistNose.of(train);
        ConsistNose.Nose trail = ConsistNose.tail(train);
        LoconauticsConstants.LOGGER.info("[maneuver] {} facing at {}: cab {} look {} axle {} lead end car {} tangent {} "
                        + "trail end car {} tangent {} rel {} direction {} sense {} flip {} speed {}",
                trainTag(), when, dirText(SableTrainDriver.headingNose(train)), dirText(SableTrainDriver.driverLook(train)),
                dirText(SableTrainDriver.axleNose(train)), shortId(lead.car().id()), dirText(ConsistNose.noseTangent(lead)),
                shortId(trail.car().id()), dirText(ConsistNose.noseTangent(trail)), plannedRel(),
                path == null ? 0 : path.direction(), ConsistNose.senseOf(train), ConsistNose.frameFlip(train),
                String.format("%.3f", train.speed()));
    }

    private void startHopLeg() {
        TurntableRegistry.Exit hop = path.hop();
        if (hop == null) {
            idleTurns = 0;
            return;
        }
        TurntableRegistry.reserve(hop.plate());
    }

    // which way the leg runs in the sense of the point the nose is on right now
    private int noseDirection() {
        int sense = train.isTrackless() ? 0 : ConsistNose.senseOf(train);
        return sense != 0 ? sense : path.direction();
    }

    // the same direction as this cars own speed sign, which is what target speed and the lever read
    private int bodyDirection() {
        return noseDirection() * ConsistNose.frameFlip(train);
    }

    private void tickInTransit(AnalogControllerBlockEntity controller) {
        if (path == null || needsReplan) {
            state = State.PRE_TRANSIT;
            return;
        }
        if (postalStopGone()) {
            quietReplan = true;
            needsReplan = true;
            state = State.PRE_TRANSIT;
            return;
        }
        if (berthCoupled()) {
            LoconauticsConstants.LOGGER.info("[maneuver] {} coupled to the train it berthed behind, arriving", trainTag());
            train.setTargetSpeed(0.0);
            train.setScheduleHold(true);
            command(controller, 0);
            arrive(controller, true);
            return;
        }
        TrackGraphLocation loc = path.target();

        RailCarriage rail = leadRail();
        // a plate leg targets the edge the parked train is already standing on, driving off it is the one thing it must not do
        if (!leftStartEdge) {
            if (path.hop() == null && onTargetEdge(loc)) {
                drive(controller);
                return;
            }
            leftStartEdge = true;
        }
        RouteDistance.Measure measured = measure(loc);
        if (!measured.known()) {
            lostMeasure(controller, measured.reason());
            return;
        }
        double remaining = measured.distance();
        lostMeasures = 0;
        if (path.hop() != null) {
            TurntableRegistry.reserve(path.hop().plate());
        }
        // a plate stop sits past the center, so its gap is the landing error
        boolean centered = centersOnPlate();
        double ahead = centered ? aheadOfTarget(loc, remaining) : remaining;
        double toStop = centered ? Math.abs(ahead + landingOffset()) : remaining;
        if (recheckRoute(Math.max(0.0, ahead))) {
            return;
        }
        tickChime(rail, remaining);

        double red = TrainSignals.gapToRed(train, bodyDirection());
        boolean redWins = !Double.isNaN(red) && red < remaining;
        if (redWins && brakeForRed(controller, red)) {
            return;
        }
        clearSignalHold();

        if (waypointLeg()) {
            if (aheadOfTarget(loc, remaining) <= ARRIVE_RADIUS) {
                passWaypoint(controller);
                return;
            }
            driveUnderCurveCap(controller);
            reportNextJunction(rail);
            return;
        }

        double v = Math.abs(train.speed());
        double brakingDist = (v * v) / (2.0 * StationLanding.decelerationFor(train));
        if (toStop <= Math.max(BRAKE_MIN_RANGE, 1.2 * brakingDist)) {
            brakeToward(controller, loc, remaining);
            return;
        }

        driveUnderCurveCap(controller);
        reportNextJunction(rail);

        if (remaining <= ARRIVE_RADIUS && path.hop() == null) {
            arrive(controller, false);
        }
    }

    // the knuckles met on the way in, so the nose now sits at the far end of the joined consist
    private boolean berthCoupled() {
        if (!maneuver || berthed.isEmpty() || path.hop() != null || train.level() == null) {
            return false;
        }
        Set<UUID> consist = TrainConsist.connectedSubLevels(train.level(), train.car().subLevelId());
        for (UUID id : berthed) {
            SableTrain wagon = SableTrainRegistry.get(id);
            if (wagon != null && wagon.car() != null && consist.contains(wagon.car().subLevelId())) {
                return true;
            }
        }
        return false;
    }

    private boolean waypointLeg() {
        return currentInstruction() instanceof WaypointInstruction && path != null
                && path.hop() == null && path.station() != null;
    }

    // a waypoint is only routed through, so the train keeps its speed and the next entry plans from here
    private void passWaypoint(AnalogControllerBlockEntity controller) {
        LoconauticsConstants.LOGGER.info("[maneuver] {} passed waypoint {} at speed {}", trainTag(), stationName(),
                String.format("%.2f", train.speed()));
        int next = currentEntry + 1;
        if (next >= schedule.entries.size()) {
            if (!schedule.cyclic) {
                finish(controller);
                return;
            }
            traceCycle("waypoint wrap");
            next = 0;
        }
        currentEntry = next;
        quietReplan = true;
        state = State.PRE_TRANSIT;
        sync();
    }

    // a route that cannot be measured replans, but never faster than every few seconds
    private void lostMeasure(AnalogControllerBlockEntity controller, String reason) {
        train.setTargetSpeed(0.0);
        command(controller, 0);
        quietReplan = true;
        state = State.PRE_TRANSIT;
        if (++lostMeasures >= MAX_LOST_MEASURES) {
            lostMeasures = 0;
            startCooldown();
            LoconauticsConstants.LOGGER.info("[schedule] {} lost its route {} times in a row, {}",
                    shortId(train.id()), MAX_LOST_MEASURES, reason);
        }
    }

    private void tickChime(RailCarriage rail, double remaining) {
        if (remaining > CHIME_ARM_RANGE) {
            chimeArmed = true;
        }
        if (!chimeArmed || remaining >= CHIME_RANGE || train.isGhost()) {
            return;
        }
        chimeArmed = false;
        chime = ArrivalChime.start(train, rail.forward().scale(path.direction()));
    }

    private boolean postalStopGone() {
        if (!postalRepeat || path.station() == null || ++postalCheckCounter < POSTAL_CHECK_TICKS) {
            return false;
        }
        postalCheckCounter = 0;
        return currentInstruction() instanceof PostalEntry entry
                && PostalPlan.canSkip(new SablePostalWorld(this), entry, path.station());
    }

    private boolean recheckRoute(double remaining) {
        if (++replanCounter < REPLAN_TICKS) {
            return false;
        }
        replanCounter = 0;
        boolean lostRoute = path.followsRoute() && !path.route().isEmpty()
                && noHopsLeft() && remaining > BRAKE_MIN_RANGE;
        if (!lostRoute && !signalParked) {
            return false;
        }
        quietReplan = true;
        needsReplan = true;
        state = State.PRE_TRANSIT;
        return true;
    }

    private boolean noHopsLeft() {
        List<Couple<TrackNode>> live = leadRail().remainingPath();
        return live == null || live.isEmpty();
    }

    private void reportNextJunction(RailCarriage rail) {
        List<Couple<TrackNode>> route = rail.remainingPath();
        TrackGraph graph = rail.graph();
        if (route == null || route.isEmpty() || graph == null) {
            return;
        }
        for (int i = 0; i < route.size(); i++) {
            Couple<TrackNode> hop = route.get(i);
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(hop.getFirst());
            if (connections == null || connections.size() <= 2) {
                continue;
            }
            Vec3 out = nodePos(hop.getSecond()).subtract(nodePos(hop.getFirst()));
            Vec3 in = i > 0
                    ? nodePos(hop.getFirst()).subtract(nodePos(route.get(i - 1).getFirst()))
                    : travelDirection(rail);
            String text = junctionText(in, out);
            if (!text.equals(lastJunction)) {
                lastJunction = text;
                announce(text);
            }
            return;
        }
        lastJunction = null;
    }

    // the nose tangent is already in world space, a rail span can still point along the old frame of a turned plate
    private Vec3 travelDirection(RailCarriage rail) {
        Vec3 tangent = ConsistNose.noseTangent(train);
        return tangent == null ? rail.forward().scale(path.direction()) : tangent.scale(path.direction());
    }

    private static Vec3 nodePos(TrackNode node) {
        return node.getLocation().getLocation();
    }

    private static String junctionText(Vec3 in, Vec3 out) {
        Vec3 from = flatten(in);
        Vec3 to = flatten(out);
        String cardinal = Direction.getNearest(to.x, 0.0, to.z).getName();
        if (from.lengthSqr() < 1.0e-6 || to.lengthSqr() < 1.0e-6) {
            return "next junction continue straight (" + cardinal + ")";
        }
        double cross = from.z * to.x - from.x * to.z;
        if (Math.abs(cross) < 0.2) {
            return "next junction continue straight (" + cardinal + ")";
        }
        return "next junction turn " + cardinal + " (" + (cross > 0 ? "left" : "right") + ")";
    }

    private static Vec3 flatten(Vec3 v) {
        Vec3 flat = new Vec3(v.x, 0.0, v.z);
        return flat.lengthSqr() < 1.0e-6 ? flat : flat.normalize();
    }

    private void drive(AnalogControllerBlockEntity controller) {
        drive(controller, bodyDirection());
    }

    private double curveCap() {
        ConsistNose.Nose nose = ConsistNose.of(train);
        TravellingPoint point = pointOf(nose);
        RailCarriage rail = nose.rail();
        TrackGraph graph = RailSeams.liveGraphOf(point, rail == null ? leadRail().graph() : rail.graph());
        return ScheduleCurveSpeed.allowedBpt(train, point, graph, path.route(), StationLanding.decelerationFor(train));
    }

    // same hold as a red signal approach, only the speed comes from the curve
    private void driveUnderCurveCap(AnalogControllerBlockEntity controller) {
        double cap = curveCap();
        if (!(cap < TrainPropulsion.gearedTopBpt(train) * maneuverScale())) {
            drive(controller);
            return;
        }
        double mag = cappedSpeed(cap);
        train.setTargetSpeed(bodyDirection() * mag);
        train.setServoBrake(Math.abs(train.speed()) > mag);
        if (controller != null) {
            command(controller, StationLanding.powerNotches(train, mag) * leverFor(bodyDirection()));
        }
    }

    private void drive(AnalogControllerBlockEntity controller, int direction) {
        if (controller == null) {
            train.setTargetSpeed(direction * ghostCruiseSpeed());
            return;
        }
        int magnitude = powerLimit < 0 ? controller.getMaxPower() : powerLimit;
        if (maneuver) {
            magnitude = Math.max(1, (int) (magnitude * maneuverScale()));
        }
        command(controller, magnitude * leverFor(direction));
    }

    private double ghostCruiseSpeed() {
        double top = train.ghostCruise();
        if (top <= 0.0) {
            top = TrainPropulsion.bearingAxleCapBpt();
        }
        if (maxNotches <= 0) {
            return top * maneuverScale();
        }
        int magnitude = powerLimit < 0 ? maxNotches : powerLimit;
        int notches = maneuver ? Math.max(1, (int) (magnitude * maneuverScale())) : magnitude;
        return top * notches / maxNotches;
    }

    private int leverFor(int railDirection) {
        double rpm = TrainPropulsion.axleRpm(train);
        int power = currentPower();
        if (power != 0 && Math.abs(rpm) > 1.0e-3) {
            leverSign = (rpm < 0.0) == (power < 0) ? 1 : -1;
            leverKnown = true;
        }
        return railDirection * (TrainPropulsion.axleSign(train) < 0.0 ? -1 : 1) * leverSign;
    }

    private boolean probeLeverSign(AnalogControllerBlockEntity controller) {
        if (controller == null || train.isGhost() || leverKnown || leverProbeTicks >= LEVER_PROBE_GIVE_UP
                || Math.abs(train.speed()) > LEVER_PROBE_MAX_SPEED) {
            return false;
        }
        double rpm = TrainPropulsion.axleRpm(train);
        if (leverProbeTicks >= LEVER_PROBE_SETTLE && currentPower() > 0 && Math.abs(rpm) > 1.0e-3) {
            leverSign = rpm < 0.0 ? -1 : 1;
            leverKnown = true;
            command(controller, 0);
            return false;
        }
        if (++leverProbeTicks >= LEVER_PROBE_GIVE_UP) {
            command(controller, 0);
            return false;
        }
        TrainBrakes.holdSoft(train);
        train.setTargetSpeed(0.0);
        command(controller, 1);
        return true;
    }

    private void command(AnalogControllerBlockEntity controller, int power) {
        if (controller == null) {
            if (power == 0) {
                train.setTargetSpeed(0.0);
            }
            return;
        }
        controller.setScheduleTargetPower(power);
        if (power != 0 && controller.getCurrentPower() == 0) {
            controller.setCurrentPower(Integer.signum(power));
        }
    }

    private void tickPostTransit(AnalogControllerBlockEntity controller) {
        if (Math.abs(train.speed()) <= 0.001 && train.currentStation() != null) {
            train.setStationState(SableTrain.StationState.STOPPED);
        }
        if (parked && controller != null && !controller.hasUser() && controller.getCurrentPower() != 0) {
            command(controller, 0);
        }
        if (parked && (controller == null || !controller.hasUser())) {
            TrainBrakes.holdSoft(train);
            if (traceNow && Math.abs(train.speed()) > StationLanding.PARK_SPEED) {
                LoconauticsConstants.LOGGER.info("[parkdrift] {} moving while parked speed={} target={} lever={} axleRpm={} braked={} hold={} powered={}",
                        shortId(train.id()), String.format("%.3f", train.speed()), String.format("%.3f", train.targetSpeed()),
                        controller == null ? "none" : controller.getCurrentPower(),
                        String.format("%.1f", TrainPropulsion.axleRpm(train)), train.isBraked(), train.brakeHold(), train.isPowered());
            }
        }
        if (driftedOffStop()) {
            ScheduleGhostTrace.event(train, "drifted off the stop, back to IN_TRANSIT, conditions restart on arrival "
                    + conditionText());
            parked = false;
            train.setScheduleHold(false);
            state = State.IN_TRANSIT;
            return;
        }

        ScheduleEntry entry = schedule.entries.get(currentEntry);
        List<List<ScheduleWaitCondition>> columns = entry.conditions;
        if (++conditionSaveCounter >= CONDITION_SAVE_TICKS) {
            conditionSaveCounter = 0;
            sync();
        }

        if (columns.isEmpty()) {
            depart(controller);
            return;
        }

        for (int i = 0; i < columns.size() && i < conditionProgress.size(); i++) {
            List<ScheduleWaitCondition> column = columns.get(i);
            int progress = conditionProgress.get(i);
            if (progress >= column.size()) {
                ScheduleGhostTrace.event(train, "column " + i + " finished, departing " + conditionText());
                depart(controller);
                return;
            }
            ScheduleWaitCondition condition = column.get(progress);
            CompoundTag context = conditionContext.get(i);
            if (condition.needsBody() && consistHasGhost(train)) {
                announcer.waitingForBody(i, columns.size(), condition);
                if (traceNow) {
                    ScheduleGhostTrace.log(train, "column " + i + " " + condition.getId().getPath()
                            + " needs the body, cars are unloaded, ctx=" + context);
                }
                continue;
            }
            announcer.waiting(i, columns.size(), condition, stationName(), context);
            boolean met = condition.tickCompletion(train.level(), train, context);
            if (traceNow) {
                ScheduleGhostTrace.log(train, "column " + i + " " + condition.getId().getPath() + " progress="
                        + progress + "/" + column.size() + " met=" + met + " ctx=" + context);
            }
            if (met) {
                announcer.met(i, columns.size(), condition, context);
                conditionProgress.set(i, progress + 1);
                conditionContext.set(i, new CompoundTag());
            }
        }
    }

    private String stateText() {
        return "entry=" + currentEntry + " paused=" + paused + " completed=" + completed + " held=" + heldReason
                + " cooldown=" + cooldown + " grace=" + transientTicks + " parked=" + parked + " path=" + (path != null)
                + " stoppedAt=" + (train.stoppedAt() == null ? "none" : train.stoppedAt().name)
                + " stationState=" + train.stationState() + " scheduleHold=" + train.scheduleHold()
                + " speed=" + String.format(java.util.Locale.ROOT, "%.3f", train.speed()) + " " + conditionText();
    }

    private String conditionText() {
        StringBuilder text = new StringBuilder("conditions=[");
        for (int i = 0; i < conditionProgress.size(); i++) {
            text.append(i == 0 ? "" : " ").append(i).append(":").append(conditionProgress.get(i)).append(conditionContext.get(i));
        }
        return text.append("]").toString();
    }

    private static boolean consistHasGhost(SableTrain train) {
        if (train.isGhost()) {
            return true;
        }
        for (UUID id : train.consistCache()) {
            SableTrain mate = SableTrainRegistry.bySubLevel(id);
            if (mate != null && mate.isGhost()) {
                return true;
            }
        }
        return false;
    }

    public boolean waitsForStationUnloaded() {
        if (paused || completed || state != State.POST_TRANSIT || schedule == null
                || currentEntry >= schedule.entries.size()) {
            return false;
        }
        List<List<ScheduleWaitCondition>> columns = schedule.entries.get(currentEntry).conditions;
        for (int i = 0; i < columns.size() && i < conditionProgress.size(); i++) {
            List<ScheduleWaitCondition> column = columns.get(i);
            int progress = conditionProgress.get(i);
            if (progress < column.size() && column.get(progress).needsStationUnloaded()) {
                return true;
            }
        }
        return false;
    }

    private boolean driftedOffStop() {
        if (!parked || path == null) {
            return false;
        }
        double remaining = measureRemaining(path.target());
        return remaining != RouteDistance.UNKNOWN
                && Math.abs(remaining + StationLanding.parkOffset) > ARRIVE_RADIUS;
    }

    private void landed(AnalogControllerBlockEntity controller) {
        if (path == null || path.hop() == null) {
            arrive(controller, true);
            return;
        }
        clearRoute();
        parked = false;
        turntableAligning = false;
        state = State.TURNTABLE;
        sync();
    }

    private void tickTurntable(AnalogControllerBlockEntity controller) {
        TurntableRegistry.Exit hop = path == null ? null : path.hop();
        Turntable turntable = hop == null ? null : TurntableRegistry.byPlate(hop.plate());
        traceTurn("enter", hop, turntable);
        if (turntable == null) {
            traceTurn("turntable gone, registered " + TurntableRegistry.all().size(), hop, null);
            skipTurn("the turntable is gone");
            return;
        }
        TurntableRegistry.reserve(hop.plate());
        train.setScheduleHold(true);
        train.setTargetSpeed(0.0);
        command(controller, 0);
        // the plate throws the car outward the whole way round, and nothing else on a stopped train resists that
        TrainBrakes.holdSoft(train);
        if (Math.abs(train.speed()) > StationLanding.PARK_SPEED) {
            traceTurn("waiting to stop", hop, turntable);
            return;
        }

        TrackGraph graph = TurntableRegistry.graphHolding(hop.movingHead());
        if (graph == null) {
            traceTurn("no graph holds the moving head", hop, turntable);
            return;
        }
        double span = TurntableRegistry.spanOf(hop.plate());
        double consistLength = TrainLength.consistLength(train.level(), train.car().subLevelId());
        if (!turntableAligning && span + TurntableRegistry.FIT_SLACK < consistLength) {
            LoconauticsConstants.LOGGER.info("[maneuver] {} plate overhang, span {} consist {}", trainTag(),
                    String.format("%.2f", span), String.format("%.2f", consistLength));
            traceTurn("overhang span " + span + " consist " + consistLength, hop, turntable);
            TrainStatusChat.turntableOverhang(train);
            return;
        }
        if (!turntableAligning && TurntableRegistry.exitOpen(graph, hop.movingHead(), hop.worldHead())) {
            traceTurn("exit already open", hop, turntable);
            advanceManeuver();
            skipTurn("the exit is already open");
            return;
        }
        if (hop.entry() && TurntableRegistry.occupied(hop.plate())) {
            traceTurn("entry waits, plate occupied", hop, turntable);
            return;
        }
        if (!turntableAligning && !hop.entry() && !TurntableRegistry.wholeTrainOn(hop.plate(), train)) {
            traceTurn("not wholly on the plate", hop, turntable);
            turntable.turntableClearAlignment();
            skipTurn("the train is not wholly on the plate");
            return;
        }
        if (!turntable.turntablePowered()) {
            traceTurn("no power", hop, turntable);
            TrainStatusChat.turntableNoPower(train);
            return;
        }
        if (!turntableAligning) {
            traceTurn("start", hop, turntable);
            LoconauticsConstants.LOGGER.info("[maneuver] {} plate turn starts, entry head {} moving head {} world head {} "
                            + "degrees {} backs out {} entry hop {} turn needed {}", trainTag(), hop.entryHead(),
                    hop.movingHead(), hop.worldHead(), String.format("%.1f", hop.degrees()),
                    ManeuverPlan.backsOut(hop), hop.entry(),
                    String.format("%.1f", TurntableRegistry.alignmentError(turntable, hop.movingHead(), hop.worldHead())));
            traceFacing("plate before turn");
            turntable.turntableAlignTo(hop.movingHead(), hop.worldHead());
            turntableAligning = true;
            return;
        }
        if (!turntable.turntableAligned()) {
            traceTurn("turning", hop, turntable);
            return;
        }
        boolean welded = TurntableRegistry.weldAligned(turntable, hop.movingHead(), hop.worldHead());
        if (!TurntableRegistry.exitOpen(graph, hop.movingHead(), hop.worldHead())) {
            traceTurn("aligned but exit not open, welded " + welded, hop, turntable);
            return;
        }
        turntable.turntableClearAlignment();
        TurntableRegistry.noteServed(hop.plate());
        LoconauticsConstants.LOGGER.info("[maneuver] {} plate turn done, error left {} degrees", trainTag(),
                String.format("%.2f", TurntableRegistry.alignmentError(turntable, hop.movingHead(), hop.worldHead())));
        traceFacing("plate after turn");
        TrainStatusChat.turntableReady(train);
        idleTurns = 0;
        advanceManeuver();
        resumeAfterTurn();
    }

    // a replan with no turn can pick the same leg again, so a streak ends in a wait
    private void skipTurn(String why) {
        LoconauticsConstants.LOGGER.info("[maneuver] {} plate turn skipped, {}", trainTag(), why);
        if (++idleTurns >= MAX_IDLE_TURNS) {
            idleTurns = 0;
            startCooldown();
            noPath("turntable", why, true);
        }
        resumeAfterTurn();
    }

    private void resumeAfterTurn() {
        if (path != null && path.hop() != null) {
            TurntableRegistry.reserve(path.hop().plate());
        }
        turntableAligning = false;
        terminalSense = 0;
        brakeSense = 0;
        lastRemaining = RouteDistance.UNKNOWN;
        quietReplan = true;
        needsReplan = true;
        state = State.PRE_TRANSIT;
    }

    private void arrive(AnalogControllerBlockEntity controller, boolean stopped) {
        if (reversalStop()) {
            turnAround(controller);
            return;
        }
        if (!stopped) {
            command(controller, 0);
        }
        if (maneuver) {
            LoconauticsConstants.LOGGER.info("[maneuver] {} arrived at {}, maneuver over", trainTag(), stationName());
            endManeuver();
        }
        clearRoute();
        parked = stopped;
        departedFrom = null;
        state = State.POST_TRANSIT;
        if (path != null && path.station() != null) {
            train.setCurrentStation(path.station());
            // the schedule lands the train itself, so trainstations never runs its own latch,
            train.setAtStation(path.station());
            if (stopped) {
                train.setStationState(SableTrain.StationState.STOPPED);
            }
            TrainMailTransfer.run(train, path.station());
        }
        if (postalRepeat) {
            postalRepeat = false;
            state = State.PRE_TRANSIT;
            startCooldown();
            sync();
            return;
        }
        loconautics$setTripped(false);
        resetConditions();
        ScheduleGhostTrace.event(train, "arrived, conditions reset entry=" + currentEntry + " stopped=" + stopped);
        if (schedule.entries.get(currentEntry).conditions.isEmpty()) {
            announcer.parked(stationName(), stopped);
        }
        sync();
    }

    private void wrapOrFinish(AnalogControllerBlockEntity controller) {
        if (currentEntry < schedule.entries.size()) {
            sync();
            return;
        }
        if (!schedule.cyclic) {
            currentEntry = schedule.entries.size() - 1;
            finish(controller);
            return;
        }
        traceCycle("wrapOrFinish");
        currentEntry = 0;
        state = State.PRE_TRANSIT;
        sync();
    }

    private void depart(AnalogControllerBlockEntity controller) {
        GlobalStation leaving = path != null && path.station() != null ? path.station() : train.atStation();
        departedFrom = leaving == null ? null : leaving.id;
        train.setScheduleHold(false);
        train.setCurrentStation(null);
        parked = false;
        brakeSense = 0;
        conditionProgress.clear();
        conditionContext.clear();
        int next = currentEntry + 1;
        if (next >= schedule.entries.size()) {
            if (!schedule.cyclic) {
                finish(controller);
                return;
            }
            traceCycle("depart wrap");
            next = 0;
        }
        currentEntry = next;
        state = State.PRE_TRANSIT;
        sync();
    }

    // temporary, tag maneuvercycle, what the maneuver state holds when a cyclic schedule wraps
    private void traceCycle(String where) {
        GlobalStation at = train == null ? null : train.atStation();
        LoconauticsConstants.LOGGER.info("[maneuver-cycle] {} {} entry={} of {} maneuver={} reversalUsed={} plan={} "
                + "plannedRel={} former={} berthed={} atStation={} speed={} state={}", trainTag(), where, currentEntry,
                schedule == null ? -1 : schedule.entries.size(), maneuver, reversalUsed, maneuverPlan, plannedRel(),
                former.size(), berthed.size(), at == null ? "none" : at.name,
                train == null ? "none" : String.format("%.2f", train.speed()), state);
    }

    private void sync() {
        if (train.level() != null) {
            ScheduleProgressStore.get(train.level().getServer()).putProgress(train.id(), saveProgress());
        }
    }

    private void finish(AnalogControllerBlockEntity controller) {
        paused = true;
        completed = true;
        train.setSpeed(0.0);
        train.setTargetSpeed(0.0);
        train.setScheduleHold(true);
        clearRoute();
        ConsistNose.forget(train);
        if (controller != null) {
            controller.clearScheduleControl();
        }
        announcer.complete();
        currentEntry = 0;
        sync();
    }

    private boolean brakeForRed(AnalogControllerBlockEntity controller, double gap) {
        double v = Math.abs(train.speed());
        double brakingDist = (v * v) / (2.0 * StationLanding.DECELERATION);
        double engageAt = Math.max(BRAKE_MIN_RANGE, 1.2 * brakingDist);
        if (gap > engageAt) {
            return false;
        }
        signalHeld = true;
        train.setScheduleHold(true);

        if (signalParked) {
            train.setTargetSpeed(0.0);
            command(controller, 0);
            return true;
        }

        if (SignalSnap.canSnap(train, gap) || SignalSnap.wentPast(gap)) {
            boolean past = SignalSnap.wentPast(gap);
            SignalSnap.snap(train, past ? 0.0 : noseDirection() * gap);
            command(controller, 0);
            signalParked = true;
            return true;
        }

        double mag = cappedSpeed(StationLanding.approachSpeed(train, gap));
        train.setTargetSpeed(bodyDirection() * mag);
        train.setServoBrake(true);
        if (controller != null) {
            command(controller, StationLanding.powerNotches(train, mag) * leverFor(bodyDirection()));
        }
        return true;
    }

    private void clearSignalHold() {
        if (!signalHeld) {
            return;
        }
        signalHeld = false;
        signalParked = false;
        train.setScheduleHold(false);
    }

    private boolean centersOnPlate() {
        return path.hop() != null && !path.hop().entry();
    }

    private double landingOffset() {
        if (path == null || path.hop() == null) {
            if (path != null && path.station() == null) {
                return 0.0;
            }
            return tailLanding ? -StationLanding.parkOffset : StationLanding.parkOffset;
        }
        return centersOnPlate() ? noseToBodyMiddle() : -ENTRY_CLEARANCE;
    }

    // the nose wheel runs this far past the plate center so the body middle ends on it
    private double noseToBodyMiddle() {
        UUID carId = train.car().subLevelId();
        Vec3 wheel = ConsistNose.wheelPos(train);
        Vec3 along = ConsistNose.noseTangent(train);
        double measured = wheel == null || along == null ? Double.NaN
                : TrainLength.toBodyMiddle(train.level(), carId, wheel, along);
        if (!Double.isNaN(measured)) {
            return measured;
        }
        Vector3d[] ref = wheel == null || TrainConsist.connectedSubLevels(train.level(), carId).size() > 1
                ? null : TrainRailAdvance.bogeyReference(null, train.car());
        return ref == null ? TrainLength.consistLength(train.level(), carId) * 0.5
                : wheel.distanceTo(new Vec3(ref[0].x, ref[0].y, ref[0].z));
    }

    // distance the nose still has to run to the target, negative once it is past it
    private double aheadOfTarget(TrackGraphLocation loc, double remaining) {
        double onEdge = signedTerminalOffset(loc);
        if (Double.isNaN(onEdge)) {
            return remaining;
        }
        if (terminalSense == 0) {
            // a plate leg lands the way it runs, the nose can already stand past the middle of the plate
            terminalSense = path.hop() != null ? noseDirection() : onEdge >= 0.0 ? 1 : -1;
        }
        return terminalSense * onEdge;
    }

    private void brakeToward(AnalogControllerBlockEntity controller, TrackGraphLocation loc, double remaining) {
        double offset = landingOffset();
        double error = aheadOfTarget(loc, remaining) + offset;

        int dir = terminalSense != 0 ? terminalSense : noseDirection();
        double gap = Math.abs(error);
        if (brakeSense == 0) {
            brakeSense = error >= 0.0 ? 1 : -1;
        }
        if (StationLanding.canSnap(train, gap) || StationLanding.wentPast(error, brakeSense)) {
            LoconauticsConstants.LOGGER.info("[nose2c] schedule {} lands error {} dir {} terminalSense {} flip {} nose car {}",
                    shortId(train.id()), String.format("%.3f", error), dir, terminalSense,
                    ConsistNose.frameFlip(train), shortId(ConsistNose.of(train).car().id()));
            traceLanding(loc, error);
            StationLanding.snapConsist(train, stopEnd(), dir * error,
                    path.hop() == null ? StationLanding.WHEEL_TOUCH_REACH : 0.0);
            traceLanding(loc, error);
            train.setScheduleHold(true);
            command(controller, 0);
            landed(controller);
            return;
        }
        if (brakeSense < 0) {
            boolean plate = path.hop() != null;
            if (centersOnPlate() && Math.abs(train.speed()) > StationLanding.PARK_SPEED) {
                // stand first, then move back to the middle
                train.setTargetSpeed(0.0);
                train.setServoBrake(true);
                command(controller, 0);
                return;
            }
            StationLanding.snapConsist(train, stopEnd(), plate ? dir * error : 0.0, StationLanding.WHEEL_TOUCH_REACH);
            train.setScheduleHold(true);
            command(controller, 0);
            landed(controller);
            return;
        }

        double approach = path.hop() != null ? StationLanding.gentleApproachSpeed(train, gap)
                : StationLanding.approachSpeed(train, gap);
        double mag = Math.min(cappedSpeed(approach), curveCap());
        double railTarget = (tailLanding ? bodyDirection() : dir * ConsistNose.frameFlip(train)) * mag;
        traceBrake(loc, remaining, error, mag);
        train.setTargetSpeed(railTarget);
        train.setServoBrake(true);
        if (controller != null) {
            command(controller, StationLanding.powerNotches(train, mag) * leverFor(railTarget < 0.0 ? -1 : 1));
        }
    }

    private void traceBrake(TrackGraphLocation loc, double remaining, double error, double mag) {
        if (!maneuver || train.level().getGameTime() % 10 != 0) {
            return;
        }
        LoconauticsConstants.LOGGER.info("[maneuver] {} brake end {} measured {} error {} offset {} speed {} approach {} "
                        + "gap to mark lead {} trail {}, to the end of the last edge lead {} trail {}", trainTag(),
                tailLanding ? "trailing" : "leading", String.format("%.2f", remaining), String.format("%.3f", error),
                String.format("%.3f", landingOffset()), String.format("%.3f", train.speed()),
                String.format("%.3f", mag), markGap(ConsistNose.of(train), loc), markGap(ConsistNose.tail(train), loc),
                endGap(ConsistNose.of(train)), endGap(ConsistNose.tail(train)));
    }

    // temporary, the real wheel to wheel gap between the leading end and the train it berthed behind
    private void traceBerthGap() {
        TrackGraph graph = leadRail().graph();
        TravellingPoint lead = pointOf(stopEnd());
        Vec3 here = graph == null || lead == null ? null : lead.getPosition(graph);
        for (UUID id : berthed) {
            SableTrain wagon = SableTrainRegistry.get(id);
            if (wagon == null || here == null) {
                continue;
            }
            for (ConsistNose.Nose end : List.of(ConsistNose.of(wagon), ConsistNose.tail(wagon))) {
                Vec3 there = end.point() == null ? null : end.point().getPosition(graph);
                LoconauticsConstants.LOGGER.info("[maneuver] {} berth gap, lead wheel {} to wheel of train {} at {} is {}",
                        trainTag(), dirText(here), shortId(id), dirText(there),
                        there == null ? "unknown" : String.format("%.2f", here.distanceTo(there)));
            }
        }
    }

    // temporary, tag platepark, where the body middle stands against the plate axis at each landing
    private void tracePlatePark(double error) {
        TurntableRegistry.Exit hop = path == null ? null : path.hop();
        TrackHostFrames.Frame frame = hop == null ? null : TrackHostFrames.frameOf(hop.plate());
        Vec3 center = train.car().carriage().moveCenter();
        Vec3 axis = frame == null ? null : frame.axis();
        Vec3 wheel = ConsistNose.wheelPos(train);
        LoconauticsConstants.LOGGER.info("[platepark] {} rel {} dir {} tailLanding {} plate {} error {} offset {} noseToMiddle {} "
                        + "bodyMiddle {} axis {} wheel {} middleToAxis {} wheelToAxis {} speed {}",
                trainTag(), plannedRel(), path == null ? 0 : path.direction(), tailLanding, hop != null && !hop.entry(),
                String.format("%.3f", error), String.format("%.3f", landingOffset()),
                hop != null && !hop.entry() ? String.format("%.3f", noseToBodyMiddle()) : "n/a", dirText(center), dirText(axis),
                dirText(wheel), center == null || axis == null ? "n/a" : String.format("%.3f", center.distanceTo(axis)),
                wheel == null || axis == null ? "n/a" : String.format("%.3f", wheel.distanceTo(axis)),
                String.format("%.3f", train.speed()));
    }

    private void traceLanding(TrackGraphLocation loc, double error) {
        if (!maneuver) {
            return;
        }
        traceBerthGap();
        tracePlatePark(error);
        LoconauticsConstants.LOGGER.info("[maneuver] {} landing on the {} end, error {} speed {}, gap to mark lead {} trail {}, "
                        + "to the end of the last edge lead {} trail {}, nose car {} tail car {}", trainTag(),
                tailLanding ? "trailing" : "leading", String.format("%.3f", error), String.format("%.3f", train.speed()),
                markGap(ConsistNose.of(train), loc), markGap(ConsistNose.tail(train), loc),
                endGap(ConsistNose.of(train)), endGap(ConsistNose.tail(train)),
                shortId(ConsistNose.of(train).car().id()), shortId(ConsistNose.tail(train).car().id()));
    }

    // temporary, how far an end still is from the far end of the last edge of the route, the track end when it is a dead end
    private String endGap(ConsistNose.Nose end) {
        List<Couple<TrackNode>> route = path == null ? null : path.route();
        TrackGraph graph = leadRail().graph();
        if (route == null || route.isEmpty() || graph == null) {
            return "unknown";
        }
        Couple<TrackNode> last = route.get(route.size() - 1);
        TrackEdge edge = graph.getConnection(last);
        if (edge == null) {
            return "unknown";
        }
        TrackGraphLocation tip = new TrackGraphLocation();
        tip.graph = graph;
        tip.edge = Couple.create(last.getFirst().getLocation(), last.getSecond().getLocation());
        tip.position = edge.getLength();
        boolean deadEnd = graph.getConnectionsFrom(last.getSecond()).size() <= 1;
        return markGap(end, tip) + (deadEnd ? " dead end" : " open");
    }

    private String markGap(ConsistNose.Nose end, TrackGraphLocation loc) {
        double gap = measureFrom(end, loc).distance();
        return gap == RouteDistance.UNKNOWN ? "unknown" : String.format("%.2f", gap);
    }

    @Nullable
    private static ServerSubLevel subLevelOf(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        UUID id = train.car().subLevelId();
        if (container == null || id == null) {
            return null;
        }
        return container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
    }


    public void status(String key, Component text) {
        if (key.equals(lastStatus)) {
            return;
        }
        lastStatus = key;
        announce(text);
    }

    public void clearStatus() {
        lastStatus = null;
        if (train != null) {
            TrainStatusChat.successfulNavigation(train);
        }
    }

    public void noPath(String filter, String why, boolean anyMatch) {
        status(anyMatch ? "no_path:" + why : "no_stops", anyMatch
                ? LoconauticsLang.translate("train.status.no_path").component()
                : CreateLang.translateDirect("schedule.no_stops"));
        if (train == null) {
            return;
        }
        if (anyMatch) {
            TrainStatusChat.failedNavigation(train);
        } else {
            TrainStatusChat.failedNavigationNoTarget(train, filter);
        }
    }

    public void announce(String text) {
        announce(Component.literal(text));
    }

    public void announce(Component text) {
        announcer.line(text);
    }

    public ScheduleAnnouncer announcer() {
        return announcer;
    }

    @Nullable
    private String stationName() {
        return path == null || path.station() == null ? null : path.station().name;
    }
}
