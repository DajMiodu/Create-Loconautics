package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import com.lycoris.loconautics.content.schedule.RouteDistance;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.schedule.SchedulePathfinder;
import com.lycoris.loconautics.content.schedule.SchedulePlanner;
import com.lycoris.loconautics.content.schedule.ScheduleProgressStore;
import com.lycoris.loconautics.trainphysics.storage.CarriageStorage;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.TrackAhead;
import com.lycoris.loconautics.trainphysics.door.TrainDoorsAccessor;
import com.lycoris.loconautics.trainphysics.signal.TrainSignalAccessor;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.NotAttachedException;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class LoconauticsTrainPeripheral implements IPeripheral, CcEvents.Target {

    private static final Map<BearingAxleBlockEntity, LoconauticsTrainPeripheral> CACHE = new WeakHashMap<>();

    private final BearingAxleBlockEntity axle;
    private final Map<String, FrequencySlot> frequencyLinks = new HashMap<>();
    private final Set<IComputerAccess> attached = new HashSet<>();
    private UUID claimedTrain;

    private LoconauticsTrainPeripheral(BearingAxleBlockEntity axle) {
        this.axle = axle;
    }

    public static LoconauticsTrainPeripheral of(BearingAxleBlockEntity axle) {
        return CACHE.computeIfAbsent(axle, LoconauticsTrainPeripheral::new);
    }

    @Override
    public String getType() {
        return "loconautics:bearing_axle";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof LoconauticsTrainPeripheral p && p.axle == axle;
    }

    @Override
    public void attach(IComputerAccess computer) {
        attached.add(computer);
        CcEvents.register(this);
    }

    // a computer that is broken, unloaded or shut down never runs its own cleanup,
    @Override
    public void detach(IComputerAccess computer) {
        attached.remove(computer);
        if (attached.isEmpty()) {
            CcEvents.unregister(this);
            releaseEverything();
        }
    }

    @Override
    public UUID watchedTrain() {
        return trainIdOrNull();
    }

    @Override
    public void queue(String event, Object[] arguments) {
        for (IComputerAccess computer : List.copyOf(attached)) {
            try {
                computer.queueEvent(event, arguments);
            } catch (NotAttachedException gone) {
                attached.remove(computer);
            }
        }
    }

    public void releaseEverything() {
        for (FrequencySlot slot : frequencyLinks.values()) {
            slot.receiver().removeFromNetwork();
            slot.transmitter().removeFromNetwork();
        }
        frequencyLinks.clear();
        UUID id = trainIdOrNull();
        // the train may not resolve any more once the computer is broken, so the claimed id is the fallback
        LoconauticsConstants.LOGGER.info("[ccsteer] release resolved={} claimed={} held={}", id, claimedTrain,
                id != null && CcControl.held(id));
        if (id != null) {
            CcControl.release(id);
        }
        if (claimedTrain != null) {
            CcControl.release(claimedTrain);
            claimedTrain = null;
        }
    }

    UUID trainIdOrNull() {
        try {
            return train().id();
        } catch (LuaException ignored) {
            return null;
        }
    }

    private SableTrain train() throws LuaException {
        ServerLevel level = (ServerLevel) axle.getLevel();
        if (level == null) throw new LuaException("the bearing axle is not loaded in any level");
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) throw new LuaException("this level has no sub levels, Sable is not tracking anything here");
        var sub = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, axle.getBlockPos());
        if (sub == null) throw new LuaException("the bearing axle is not in a sub level, the carriage has to be assembled");
        UUID subId = sub.getUniqueId();
        SableTrain train = SableTrainRegistry.bySubLevel(subId);
        if (train == null) throw new LuaException("this carriage is not on rails as a train, place it on a track first");
        return train;
    }

    void requireLive() throws LuaException {
        train();
    }

    private SableScheduleRuntime schedule(SableTrain train) {
        return SableScheduleRuntime.forConsist(train);
    }

    private ServerSubLevel resolveSubLevel(SableTrain train) {
        return SubLevelData.resolve(train.level(), train.car().subLevelId());
    }

    @LuaFunction(mainThread = true)
    public String getId() throws LuaException {
        return train().id().toString();
    }

    @LuaFunction(mainThread = true)
    public String getName() throws LuaException {
        SableTrain train = train();
        return SableTrainSpawner.trainName(train.level(), train);
    }

    @LuaFunction(mainThread = true)
    public void setName(String name) throws LuaException {
        SableTrain train = train();
        SableTrainSpawner.nameConsist(train.level(), train, name == null ? "" : name);
    }

    @LuaFunction(mainThread = true)
    public int getCarriageCount() throws LuaException {
        SableTrain train = train();
        return ConsistAccess.carriages(train.level(), train.car().subLevelId()).size();
    }

    @LuaFunction(mainThread = true)
    public double getSpeed() throws LuaException {
        return train().speed();
    }

    @LuaFunction(mainThread = true)
    public double getTargetSpeed() throws LuaException {
        return train().targetSpeed();
    }

    @LuaFunction(mainThread = true)
    public double getAccel() throws LuaException {
        return train().measuredAccel();
    }

    @LuaFunction(mainThread = true)
    public double getAccelLimit() throws LuaException {
        SableTrain train = train();
        LoconauticsConstants.LOGGER.info("[ccaccel] limit={} speed={} target={} hold={} braked={} derailed={} physics={} mass={}",
                train.accel(), train.speed(), train.targetSpeed(), train.brakeHold(), train.isBraked(),
                train.isDerailed(), train.isPhysics(), TrainConsist.totalMass(train.level(), train.car().subLevelId()));
        return train.accel();
    }

    @LuaFunction(mainThread = true)
    public double getMass() throws LuaException {
        SableTrain train = train();
        return TrainConsist.totalMass(train.level(), train.car().subLevelId());
    }

    @LuaFunction(mainThread = true)
    public double getStressRatio() throws LuaException {
        return axle.getNetworkStressRatio();
    }

    @LuaFunction(mainThread = true)
    public double getSlopeAngle() throws LuaException {
        return Math.abs(getPitch());
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered() throws LuaException {
        return train().isPowered();
    }

    @LuaFunction(mainThread = true)
    public boolean isRailed() throws LuaException {
        return !train().isDerailed();
    }

    @LuaFunction(mainThread = true)
    public boolean isDerailed() throws LuaException {
        return train().isDerailed();
    }

    @LuaFunction(mainThread = true)
    public boolean isBraked() throws LuaException {
        return train().isBraked();
    }

    @LuaFunction(mainThread = true)
    public boolean isBrakeHard() throws LuaException {
        return train().brakeHard();
    }

    @LuaFunction(mainThread = true)
    public boolean isScheduleHeld() throws LuaException {
        return train().scheduleHold();
    }

    @LuaFunction(mainThread = true)
    public int getSteerInput() throws LuaException {
        CcControl control = CcControl.get(train().id());
        Integer steer = control == null ? null : control.steer();
        return steer != null ? steer : axle.getSteerInput();
    }

    @LuaFunction(mainThread = true)
    public boolean isAtStation() throws LuaException {
        return train().isAtStation();
    }

    @LuaFunction(mainThread = true)
    public String getStationState() throws LuaException {
        return switch (train().stationState()) {
            case RUNNING   -> "running";
            case STOPPING  -> "stopping";
            case STOPPED   -> "stopped";
            case DEPARTING -> "departing";
        };
    }

    @LuaFunction(mainThread = true)
    public String getStationName() throws LuaException {
        SableTrain train = train();
        GlobalStation s = train.atStation() != null ? train.atStation() : train.currentStation();
        return s != null ? s.name : null;
    }

    @LuaFunction(mainThread = true)
    public String getTargetStationName() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        if (rt == null) return null;
        GlobalStation t = rt.targetStation();
        return t != null ? t.name : null;
    }

    @LuaFunction(mainThread = true)
    public Double getDisToStation(IArguments args) throws LuaException {
        SableTrain train = train();
        String stationName = args.optString(0).orElse(null);
        if (stationName == null || stationName.isEmpty()) {
            return nearestStationDistance(train);
        }
        GlobalStation target = findStation(stationName);
        return pathDistance(train, SchedulePlanner.locationOf(noseGraph(train), target));
    }

    private Double nearestStationDistance(SableTrain train) throws LuaException {
        TrackGraph graph = noseGraph(train);
        Double best = null;
        for (GlobalStation station : graph.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.STATION)) {
            Double distance;
            try {
                distance = pathDistance(train, SchedulePlanner.locationOf(graph, station));
            } catch (LuaException unreachable) {
                continue;
            }
            if (distance != null && (best == null || distance < best)) {
                best = distance;
            }
        }
        return best;
    }

    @LuaFunction(mainThread = true)
    public int getDwellTicks() throws LuaException {
        return train().dwellTicks();
    }

    @LuaFunction(mainThread = true)
    public Double getGapToRed() throws LuaException {
        double gap = TrainSignalAccessor.gapToRed(train());
        return Double.isNaN(gap) ? null : gap;
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextSignal() throws LuaException {
        TrainSignalAccessor.SignalAhead ahead = TrainSignalAccessor.nextSignal(train());
        return ahead == null ? null : CcNetwork.nextSignal(ahead);
    }

    @LuaFunction(mainThread = true)
    public boolean isHeldAtRed() throws LuaException {
        return TrainSignalAccessor.isHeldAtRed(train());
    }

    private TrackAhead.Report trackAhead(IArguments args, int rangeIndex) throws LuaException {
        return TrackAhead.scan(train(), CcTrackAhead.range(args, rangeIndex));
    }

    @LuaFunction(mainThread = true)
    public String getTrackType() throws LuaException {
        TrackAhead.Report report = TrackAhead.scan(train(), 1.0);
        return report == null ? null : CcTrackAhead.typeOf(report.startHost());
    }

    @LuaFunction(mainThread = true)
    public String getTrackSubLevelId() throws LuaException {
        TrackAhead.Report report = TrackAhead.scan(train(), 1.0);
        return report == null || report.startHost() == null ? null : report.startHost().toString();
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getTrackEnd(IArguments args) throws LuaException {
        return CcTrackAhead.end(trackAhead(args, 0));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToTrackEnd(IArguments args) throws LuaException {
        return CcTrackAhead.distance(getTrackEnd(args));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextCurve(IArguments args) throws LuaException {
        TrackAhead.Report report = trackAhead(args, 0);
        return report == null ? null : CcTrackAhead.curve(TrackAhead.nextCurve(report));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToCurve(IArguments args) throws LuaException {
        return CcTrackAhead.distance(getNextCurve(args));
    }

    @LuaFunction(mainThread = true)
    public Double getSlopeAhead(IArguments args) throws LuaException {
        double distance = args.getDouble(0);
        if (distance < 0.0 || distance > TrackAhead.MAX_RANGE) {
            throw new LuaException("distance must be between 0 and " + (int) TrackAhead.MAX_RANGE + " blocks");
        }
        TrackAhead.Report report = TrackAhead.scan(train(), Math.max(distance, 1.0));
        Double slope = report == null ? null : TrackAhead.slopeAt(report, distance);
        return slope == null ? null : hundredths(slope);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextSlopeChange(IArguments args) throws LuaException {
        double minDelta = args.optDouble(0).orElse(TrackAhead.DEFAULT_SLOPE_DELTA);
        if (minDelta <= 0.0) {
            throw new LuaException("minDelta must be above 0 degrees");
        }
        TrackAhead.Report report = trackAhead(args, 1);
        return report == null ? null : CcTrackAhead.slopeChange(TrackAhead.nextSlopeChange(report, minDelta));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToSlopeChange(IArguments args) throws LuaException {
        return CcTrackAhead.distance(getNextSlopeChange(args));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextTrackChange(IArguments args) throws LuaException {
        TrackAhead.Report report = trackAhead(args, 0);
        return report == null ? null : CcTrackAhead.change(TrackAhead.firstChange(report));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextSubLevelTrack(IArguments args) throws LuaException {
        TrackAhead.Report report = trackAhead(args, 0);
        return report == null ? null : CcTrackAhead.change(TrackAhead.nextChange(report, true));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToSubLevelTrack(IArguments args) throws LuaException {
        return CcTrackAhead.distance(getNextSubLevelTrack(args));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextWorldTrack(IArguments args) throws LuaException {
        TrackAhead.Report report = trackAhead(args, 0);
        return report == null ? null : CcTrackAhead.change(TrackAhead.nextChange(report, false));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToWorldTrack(IArguments args) throws LuaException {
        return CcTrackAhead.distance(getNextWorldTrack(args));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToSignal(IArguments args) throws LuaException {
        SableTrain train = train();
        String state = args.optString(0).orElse(null);
        double gap;
        if (state == null || state.isEmpty()) {
            gap = TrainSignalAccessor.gapToAnySignal(train);
        } else if (state.equals("red")) {
            gap = TrainSignalAccessor.gapToRed(train);
        } else {
            throw new LuaException("state must be \"red\" or omitted for any signal");
        }
        return Double.isNaN(gap) ? null : gap;
    }

    @LuaFunction(mainThread = true)
    public boolean doorsOpen() throws LuaException {
        return train().doorsOpen();
    }

    @LuaFunction(mainThread = true)
    public void setDoors(boolean open) throws LuaException {
        SableTrain train = train();
        control(train).setDoors(open);
        TrainDoorsAccessor.setDoors(train, open);
    }

    @LuaFunction(mainThread = true)
    public void releaseDoors() throws LuaException {
        CcControl control = CcControl.get(train().id());
        if (control != null) {
            control.setDoors(null);
        }
    }

    @LuaFunction(mainThread = true)
    public double getYaw() throws LuaException {
        return hundredths(SubLevelData.yawDeg(railOrientation(), axleForward()));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getWheelLift() throws LuaException {
        SableTrain train = train();
        ServerSubLevel sub = resolveSubLevel(train);
        com.lycoris.loconautics.trainphysics.glue.CarriageRoll.Roll roll =
                com.lycoris.loconautics.trainphysics.glue.CarriageRoll.rollOf(sub);
        Map<String, Object> out = new HashMap<>();
        boolean rolling = roll != null && roll.rolling;
        out.put("rolling", rolling);
        out.put("lock", roll == null ? "none" : roll.lock);
        out.put("rollDegrees", rolling ? hundredths(Math.toDegrees(roll.phi)) : 0.0);
        out.put("tipDegrees", rolling ? hundredths(Math.toDegrees(roll.tip)) : 0.0);
        out.put("ungluesAtDegrees", rolling ? hundredths(Math.toDegrees(roll.unglue)) : 0.0);
        out.put("liftDegrees", rolling ? hundredths(Math.abs(Math.toDegrees(roll.phi))) : 0.0);
        out.put("holdDegrees", hundredths(Math.toDegrees(com.lycoris.loconautics.trainphysics.glue.CarriageRoll.holdAngle())));
        out.put("predictedLiftDegrees", rolling ? hundredths(Math.toDegrees(roll.predicted)) : 0.0);
        out.put("side", !rolling || Math.abs(roll.phi) < 1.0e-3 ? "none" : leansRight(train, sub, roll.phi) ? "right" : "left");
        out.put("suspensionDegrees", rolling ? hundredths(Math.toDegrees(roll.suspension)) : 0.0);
        out.put("couplerTorque", rolling ? hundredths(roll.couplerTorque) : 0.0);
        out.put("couplerTwistDegrees", rolling ? hundredths(Math.toDegrees(roll.couplerTwist)) : 0.0);
        out.put("liftedWheels", rolling ? roll.lifted : 0);
        out.put("wheels", rolling ? roll.y.length : 0);
        return out;
    }

    // the roll is signed against the rail chord, the car may face the other way along it
    private static boolean leansRight(SableTrain train, ServerSubLevel sub, double phi) {
        Vec3 nose = SableTrainDriver.axleNose(train);
        Vector3d[] ref = nose == null || sub == null ? null : TrainRailAdvance.bogeyReference(sub, train.car());
        boolean facesAgainstChord = ref != null && nose.x * ref[1].x + nose.y * ref[1].y + nose.z * ref[1].z < 0.0;
        return (phi > 0.0) != facesAgainstChord;
    }

    @LuaFunction(mainThread = true)
    public double getPitch() throws LuaException {
        return hundredths(SubLevelData.pitchDeg(railOrientation(), axleForward()));
    }

    @LuaFunction(mainThread = true)
    public double getRoll() throws LuaException {
        return hundredths(SubLevelData.rollDeg(railOrientation(), axleForward()));
    }

    // sables pose of the body with the setbody tilt taken back off, the tweak is multiplied on last so its inverse
    private Quaterniond railOrientation() throws LuaException {
        SableTrain train = train();
        ServerSubLevel sub = resolveSubLevel(train);
        if (sub == null) throw new LuaException("sub level not available");
        RailCarriage rail = train.car().carriage();
        Quaterniond tweak = train.poseTweak().local(rail == null ? null : rail.referenceForward());
        return new Quaterniond(SubLevelData.orientation(sub)).mul(tweak.invert());
    }

    private Vector3d axleForward() {
        Direction front = BearingAxleBlock.front(axle.getBlockState());
        return new Vector3d(front.getStepX(), 0.0, front.getStepZ());
    }

    private static double hundredths(double degrees) {
        return Math.round(degrees * 100.0) / 100.0;
    }

    @LuaFunction(mainThread = true)
    public boolean isUpright(double minDot) throws LuaException {
        ServerSubLevel sub = resolveSubLevel(train());
        if (sub == null) throw new LuaException("sub level not available");
        return SubLevelData.isUpright(sub, minDot);
    }

    @LuaFunction(mainThread = true)
    public double getBodyRoll() throws LuaException {
        return train().poseTweak().targetRoll();
    }

    @LuaFunction(mainThread = true)
    public void setBodyRoll(double degrees) throws LuaException {
        train().poseTweak().setRoll(bodyAngle("roll", degrees));
    }

    @LuaFunction(mainThread = true)
    public double getBodyPitch() throws LuaException {
        return train().poseTweak().targetPitch();
    }

    @LuaFunction(mainThread = true)
    public void setBodyPitch(double degrees) throws LuaException {
        train().poseTweak().setPitch(bodyAngle("pitch", degrees));
    }

    @LuaFunction(mainThread = true)
    public double getBodyYaw() throws LuaException {
        return train().poseTweak().targetYaw();
    }

    @LuaFunction(mainThread = true)
    public void setBodyYaw(double degrees) throws LuaException {
        train().poseTweak().setYaw(bodyAngle("yaw", degrees));
    }

    @LuaFunction(mainThread = true)
    public double getBodyX() throws LuaException {
        return train().poseTweak().targetX();
    }

    @LuaFunction(mainThread = true)
    public void setBodyX(double blocks) throws LuaException {
        PoseTweak tweak = train().poseTweak();
        tweak.setOffset(bodyOffset("x", blocks), tweak.targetY(), tweak.targetZ());
    }

    @LuaFunction(mainThread = true)
    public double getBodyY() throws LuaException {
        return train().poseTweak().targetY();
    }

    @LuaFunction(mainThread = true)
    public void setBodyY(double blocks) throws LuaException {
        PoseTweak tweak = train().poseTweak();
        tweak.setOffset(tweak.targetX(), bodyOffset("y", blocks), tweak.targetZ());
    }

    @LuaFunction(mainThread = true)
    public double getBodyZ() throws LuaException {
        return train().poseTweak().targetZ();
    }

    @LuaFunction(mainThread = true)
    public void setBodyZ(double blocks) throws LuaException {
        PoseTweak tweak = train().poseTweak();
        tweak.setOffset(tweak.targetX(), tweak.targetY(), bodyOffset("z", blocks));
    }

    @LuaFunction(mainThread = true)
    public void setBodyOffset(double x, double y, double z) throws LuaException {
        train().poseTweak().setOffset(bodyOffset("x", x), bodyOffset("y", y), bodyOffset("z", z));
    }

    @LuaFunction(mainThread = true)
    public void setBodyLerpSpeed(double speed) throws LuaException {
        train().poseTweak().setLerpSpeed(bodyRange("speed", speed, 0.0, 1.0));
    }

    private static double bodyAngle(String name, double degrees) throws LuaException {
        return bodyRange(name, degrees, -PoseTweak.MAX_ANGLE, PoseTweak.MAX_ANGLE);
    }

    private static double bodyOffset(String name, double blocks) throws LuaException {
        return bodyRange(name, blocks, -PoseTweak.MAX_OFFSET, PoseTweak.MAX_OFFSET);
    }

    private static double bodyRange(String name, double value, double min, double max) throws LuaException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new LuaException(name + " must be a real number");
        }
        if (value < min || value > max) {
            throw new LuaException(String.format("%s must be between %s and %s", name,
                    trim(min), trim(max)));
        }
        return value;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    @LuaFunction(mainThread = true)
    public double getBodyLerpSpeed() throws LuaException {
        return train().poseTweak().lerpSpeed();
    }

    @LuaFunction(mainThread = true)
    public boolean isBodySettled() throws LuaException {
        return train().poseTweak().isSettled();
    }

    @LuaFunction(mainThread = true)
    public void resetBody() throws LuaException {
        train().poseTweak().reset();
    }

    @LuaFunction(mainThread = true)
    public Map<String, Double> getVelocity() throws LuaException {
        ServerSubLevel sub = resolveSubLevel(train());
        if (sub == null) throw new LuaException("sub level not available");
        Vector3d vel = SubLevelData.linearVelocity(sub);
        if (vel == null) throw new LuaException("velocity not available, body may not be simulated");
        return Map.of("x", vel.x, "y", vel.y, "z", vel.z);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Double> getWorldPos() throws LuaException {
        ServerSubLevel sub = resolveSubLevel(train());
        if (sub == null) throw new LuaException("sub level not available");
        Vector3d pos = SubLevelData.position(sub);
        return Map.of("x", pos.x, "y", pos.y, "z", pos.z);
    }

    @LuaFunction(mainThread = true)
    public List<Map<String, Double>> getWorldPosOf(String blockId) throws LuaException {
        SableTrain train = train();
        List<Map<String, Double>> results = new ArrayList<>();
        for (UUID id : ConsistAccess.carriages(train.level(), train.car().subLevelId())) {
            ServerSubLevel sub = SubLevelData.resolve(train.level(), id);
            if (sub == null) continue;
            for (var be : SubLevelBlocks.blockEntities(sub)) {
                String beId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(be.getBlockState().getBlock()).toString();
                if (!beId.equals(blockId)) continue;
                Vector3d world = SubLevelData.worldOfBlock(sub, be.getBlockPos());
                results.add(Map.of("x", world.x, "y", world.y, "z", world.z));
            }
        }
        return results;
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getDrivetrain() throws LuaException {
        return CcDrivetrain.report(CcDrivetrain.check(axle.getLevel(), axle.getBlockPos()));
    }

    @LuaFunction(mainThread = true)
    public TransmissionPeripheral getTransmission() throws LuaException {
        LocomotiveHardware.Drivetrain drivetrain = CcDrivetrain.check(axle.getLevel(), axle.getBlockPos());
        if (drivetrain.transmission() == null) {
            throw new LuaException(CcDrivetrain.describe(drivetrain.problem()));
        }
        return TransmissionPeripheral.of(drivetrain.transmission());
    }

    @LuaFunction(mainThread = true)
    public double getTargetRpm() throws LuaException {
        return getTransmission().getTargetRpm();
    }

    private void requireDrivetrain() throws LuaException {
        CcDrivetrain.require(axle.getLevel(), axle.getBlockPos());
    }

    @LuaFunction(mainThread = true)
    public void setManualControl(boolean enabled) throws LuaException {
        SableTrain train = train();
        if (enabled) {
            requireDrivetrain();
            claimedTrain = train.id();
            CcControl.claimClean(train.id()).setTargetSpeed(train.targetSpeed());
            TrainAdvancements.aiTakeover(train);
            return;
        }
        CcControl.release(train.id());
    }

    @LuaFunction(mainThread = true)
    public void release() throws LuaException {
        releaseEverything();
    }

    @LuaFunction(mainThread = true)
    public boolean isManualControl() throws LuaException {
        return CcControl.held(train().id());
    }

    @LuaFunction(mainThread = true)
    public void setTargetSpeed(double speed) throws LuaException {
        SableTrain train = train();
        CcControl control = held(train);
        requireDrivetrain();
        control.setTargetSpeed(speed);
    }

    @LuaFunction(mainThread = true)
    public void setAccel(double accel) throws LuaException {
        held(train()).setAccel(Math.abs(accel));
    }

    @LuaFunction(mainThread = true)
    public void setBrakeScale(double scale) throws LuaException {
        if (scale < 0.0 || scale > 1.0) throw new LuaException("brake scale must be 0 to 1");
        held(train()).setBrakeScale(scale);
    }

    @LuaFunction(mainThread = true)
    public int stop(IArguments args) throws LuaException {
        SableTrain train = train();
        return control(train).stopAt(train, args.optBoolean(0).orElse(false), Double.NaN);
    }

    @LuaFunction(mainThread = true)
    public int stopIn(IArguments args) throws LuaException {
        double distance = args.getDouble(0);
        if (!(distance > 0.0)) {
            throw new LuaException("distance must be more than 0 blocks");
        }
        SableTrain train = train();
        return control(train).stopAt(train, false, distance);
    }

    @LuaFunction(mainThread = true)
    public boolean isStopping() throws LuaException {
        CcControl control = CcControl.get(train().id());
        return control != null && control.isStopping();
    }

    @LuaFunction(mainThread = true)
    public double getBrakingDistance() throws LuaException {
        SableTrain train = train();
        double speed = Math.abs(train.speed());
        return speed * speed / (2.0 * StationLanding.decelerationFor(train));
    }

    @LuaFunction(mainThread = true)
    public double getEmergencyBrakingDistance() throws LuaException {
        return StationLanding.hardStopDistance(train());
    }

    @LuaFunction(mainThread = true)
    public double getServiceDecel() throws LuaException {
        return StationLanding.decelerationFor(train());
    }

    @LuaFunction(mainThread = true)
    public double getEmergencyDecel() throws LuaException {
        return StationLanding.emergencyDecel();
    }

    @LuaFunction(mainThread = true)
    public double getSlope() throws LuaException {
        double direction = train().speed() < -1.0e-4 ? -1.0 : 1.0;
        return -getPitch() * direction;
    }

    @LuaFunction(mainThread = true)
    public void steer(int dir) throws LuaException {
        if (dir < -1 || dir > 1) throw new LuaException("steer dir must be -1, 0, or 1");
        LoconauticsConstants.LOGGER.info("[ccsteer] steer {} on {}", dir, trainIdOrNull());
        held(train()).setSteer(dir);
    }

    @LuaFunction(mainThread = true)
    public String getScheduleState() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        if (rt == null) return null;
        return switch (rt.state) {
            case PRE_TRANSIT  -> "pre_transit";
            case IN_TRANSIT   -> "in_transit";
            case TURNTABLE    -> "turntable";
            case POST_TRANSIT -> "post_transit";
        };
    }

    @LuaFunction(mainThread = true)
    public int getScheduleEntry() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        return rt == null ? 0 : rt.currentEntry + 1;
    }

    @LuaFunction(mainThread = true)
    public int getScheduleSize() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        if (rt == null || rt.schedule == null) return 0;
        return rt.schedule.entries.size();
    }

    @LuaFunction(mainThread = true)
    public double getRemainingToTarget() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        return rt == null ? -1.0 : rt.remainingToTarget();
    }

    @LuaFunction(mainThread = true)
    public boolean isPaused() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        return rt != null && rt.paused;
    }

    @LuaFunction(mainThread = true)
    public boolean isCompleted() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        return rt != null && rt.completed;
    }

    @LuaFunction(mainThread = true)
    public String getHeldReason() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        return rt == null ? null : rt.heldReason();
    }

    @LuaFunction(mainThread = true)
    public void setSchedule(IArguments args) throws LuaException {
        Map<?, ?> table = args.getTable(0);
        SableTrain train = train();
        ServerSubLevel sub = resolveSubLevel(train);
        if (sub == null) {
            throw new LuaException("sub level not available");
        }
        AnalogControllerBlockEntity controller = SubLevelBlocks.find(sub, AnalogControllerBlockEntity.class);
        if (controller == null) {
            throw new LuaException("this cart has no analog controller, a schedule has nothing to drive");
        }
        LocomotiveHardware.Result hardware = LocomotiveHardware.validate(sub, controller);
        if (!hardware.ok()) {
            throw new LuaException(hardware.messageKey());
        }
        Schedule schedule = CcSchedule.fromLua(train.level().registryAccess(), table);
        UUID key = SableScheduleRuntime.consistKey(train.id());
        ScheduleProgressStore.get(train.level().getServer())
                .putSchedule(key, schedule.write(train.level().registryAccess()));
        SableScheduleRuntime.assign(key, schedule);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getSchedule() throws LuaException {
        SableScheduleRuntime rt = schedule(train());
        if (rt == null || rt.schedule == null) {
            throw new LuaException("this train has no schedule, call setSchedule first");
        }
        return CcSchedule.toLua(rt.schedule);
    }

    @LuaFunction(mainThread = true)
    public void clearSchedule() throws LuaException {
        SableTrain train = train();
        UUID key = SableScheduleRuntime.consistKey(train.id());
        SableScheduleRuntime.clear(key);
        ScheduleProgressStore.get(train.level().getServer()).remove(key);
    }

    @LuaFunction(mainThread = true)
    public void pauseSchedule() throws LuaException {
        running(train()).paused = true;
    }

    @LuaFunction(mainThread = true)
    public void resumeSchedule() throws LuaException {
        SableScheduleRuntime rt = running(train());
        rt.paused = false;
        rt.state = SableScheduleRuntime.State.PRE_TRANSIT;
    }

    @LuaFunction(mainThread = true)
    public Double getDisToCoord(double x, double y, double z) throws LuaException {
        SableTrain train = train();
        EdgeTarget target = nearestEdgeTarget(noseGraph(train), x, y, z);
        return pathDistance(train, target.location());
    }

    @LuaFunction(mainThread = true)
    public List<String> getStepsToStation(String stationName) throws LuaException {
        SableTrain train = train();
        GlobalStation target = findStation(stationName);
        Couple<TrackNode> nodes = stationNodes(train, target);
        return stepsTo(train, nodes.getFirst(), nodes.getSecond());
    }

    @LuaFunction(mainThread = true)
    public List<String> getStepsToCoord(double x, double y, double z) throws LuaException {
        SableTrain train = train();
        var target = nearestEdgeNodes(noseGraph(train), x, y, z);
        return stepsTo(train, target.getFirst(), target.getSecond());
    }

    @LuaFunction(mainThread = true)
    public boolean parkAtStation(String stationName) throws LuaException {
        SableTrain train = train();
        requireDrivetrain();
        GlobalStation station = findStation(stationName);
        TrackGraph graph = noseGraph(train);
        // routed not scanned, a scan only walks one side of a junction
        SchedulePath path = SchedulePlanner.planTo(graph, nosePoint(train),
                SchedulePlanner.locationOf(graph, station), station, true);
        if (path == null) {
            throw new LuaException("no route from here to station \"" + stationName + "\", the track may be "
                    + "broken between the two or the station only takes trains from its other side");
        }
        control(train).parkAt(path, TrainStations.noseRail(train));
        return true;
    }

    @LuaFunction
    public MethodResult parkAtCoord(ILuaContext context, double x, double y, double z) throws LuaException {
        long taskId = context.issueMainThreadTask(() -> startParkAtCoord(x, y, z));
        return awaitTask(taskId);
    }

    // cc rejects a yielding result from a mainthread function
    private static MethodResult awaitTask(long taskId) {
        return MethodResult.pullEvent("task_complete", event -> {
            if (event.length < 3 || !(event[1] instanceof Number id) || id.longValue() != taskId) {
                return awaitTask(taskId);
            }
            if (!Boolean.TRUE.equals(event[2])) {
                throw new LuaException(event.length > 3 && event[3] != null ? event[3].toString() : "park failed");
            }
            return awaitPark((String) event[3], ((Number) event[4]).intValue());
        });
    }

    private Object[] startParkAtCoord(double x, double y, double z) throws LuaException {
        SableTrain train = train();
        requireDrivetrain();
        TrackGraph graph = noseGraph(train);
        EdgeTarget target = nearestEdgeTarget(graph, x, y, z);
        if (target.miss() > ON_TRACK_REACH) {
            throw new LuaException("there is no track at " + trim(x) + ", " + trim(y) + ", " + trim(z)
                    + " on this train's track graph, the nearest rail is " + Math.round(target.miss())
                    + " blocks away");
        }
        // the same routing a schedule waypoint card plans with, undirected because a coordinate has no side
        SchedulePath path = SchedulePlanner.planTo(graph, nosePoint(train), target.location(), null, false);
        if (path == null) {
            throw new LuaException("no route from here to the track nearest " + trim(x) + ", " + trim(y) + ", "
                    + trim(z) + ", the track may be broken between the two");
        }
        int parkId = control(train).parkAt(path, TrainStations.noseRail(train));
        return new Object[] { train.id().toString(), parkId };
    }

    // train id and park id ride the event so a stale park cant answer it
    private static MethodResult awaitPark(String trainId, int parkId) {
        return MethodResult.pullEvent(CcControl.PARKED_EVENT, event -> {
            boolean ours = event.length >= 4 && trainId.equals(event[1])
                    && event[3] instanceof Number id && id.intValue() == parkId;
            return ours ? MethodResult.of(Boolean.TRUE.equals(event[2])) : awaitPark(trainId, parkId);
        });
    }

    @LuaFunction(mainThread = true)
    public boolean isParking() throws LuaException {
        CcControl control = CcControl.get(train().id());
        return control != null && control.isParking();
    }

    @LuaFunction(mainThread = true)
    public int getItemCount(IArguments args) throws LuaException {
        return countItems(train(), ConsistAccess.WHOLE_CONSIST, args.optString(0).orElse(null));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Integer> getItems() throws LuaException {
        return collectItems(train(), ConsistAccess.WHOLE_CONSIST);
    }

    @LuaFunction(mainThread = true)
    public int getFluidAmount(IArguments args) throws LuaException {
        return countFluid(train(), ConsistAccess.WHOLE_CONSIST, args.optString(0).orElse(null));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Map<String, Integer>> getFluids() throws LuaException {
        return collectFluids(train(), ConsistAccess.WHOLE_CONSIST);
    }

    @LuaFunction(mainThread = true)
    public int getCarriageItemCount(IArguments args) throws LuaException {
        return carriageItemCount(args.getInt(0), args.optString(1).orElse(null));
    }

    int carriageItemCount(int index, String filterId) throws LuaException {
        SableTrain train = train();
        requireCarriage(train, index);
        return countItems(train, index - 1, filterId);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Integer> getCarriageItems(int index) throws LuaException {
        SableTrain train = train();
        requireCarriage(train, index);
        return collectItems(train, index - 1);
    }

    @LuaFunction(mainThread = true)
    public int getCarriageFluidAmount(IArguments args) throws LuaException {
        return carriageFluidAmount(args.getInt(0), args.optString(1).orElse(null));
    }

    int carriageFluidAmount(int index, String filterId) throws LuaException {
        SableTrain train = train();
        requireCarriage(train, index);
        return countFluid(train, index - 1, filterId);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Map<String, Integer>> getCarriageFluids(int index) throws LuaException {
        SableTrain train = train();
        requireCarriage(train, index);
        return collectFluids(train, index - 1);
    }

    @LuaFunction(mainThread = true)
    public int getCarriageItemCapacity(int index) throws LuaException {
        SableTrain train = train();
        ServerSubLevel sub = requireCarriage(train, index);
        int cap = 0;
        for (IItemHandler h : CarriageStorage.items(sub))
            for (int slot = 0; slot < h.getSlots(); slot++)
                cap += h.getSlotLimit(slot);
        return cap;
    }

    @LuaFunction(mainThread = true)
    public int getCarriageFluidCapacity(int index) throws LuaException {
        SableTrain train = train();
        ServerSubLevel sub = requireCarriage(train, index);
        int cap = 0;
        for (IFluidHandler h : CarriageStorage.fluids(sub))
            for (int tank = 0; tank < h.getTanks(); tank++)
                cap += h.getTankCapacity(tank);
        return cap;
    }

    @LuaFunction(mainThread = true)
    public int getCarriageBlockCount(int index) throws LuaException {
        return SubLevelBlocks.countBlocks(requireCarriage(train(), index));
    }

    @LuaFunction(mainThread = true)
    public int getCarriagePassengerCount(int index) throws LuaException {
        SableTrain train = train();
        requireCarriage(train, index);
        return ConsistAccess.countPlayersAboard(train.level(), train.car().subLevelId(), index - 1);
    }

    @LuaFunction(mainThread = true)
    public boolean getCarriageHasPassengers(int index) throws LuaException {
        return getCarriagePassengerCount(index) > 0;
    }

    @LuaFunction(mainThread = true)
    public double getLength() throws LuaException {
        SableTrain train = train();
        return TrainLength.consistLength(train.level(), train.car().subLevelId());
    }

    @LuaFunction(mainThread = true)
    public double getWidth() throws LuaException {
        SableTrain train = train();
        return TrainLength.consistWidth(train.level(), train.car().subLevelId());
    }

    @LuaFunction(mainThread = true)
    public double getHeight() throws LuaException {
        SableTrain train = train();
        return TrainLength.consistHeight(train.level(), train.car().subLevelId());
    }

    private SableTrain carriageTrain(int index) throws LuaException {
        SableTrain train = train();
        requireCarriage(train, index);
        ServerSubLevel sub = ConsistAccess.carriage(train.level(), train.car().subLevelId(), index - 1);
        SableTrain car = sub == null ? null : SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (car == null || car.car() == null) {
            throw new LuaException("that carriage is not on a track");
        }
        return car;
    }

    public double getCarriageLength(int index) throws LuaException {
        return TrainLength.carLength(train().level(), carriageTrain(index));
    }

    public double getCarriageWidth(int index) throws LuaException {
        return TrainLength.carWidth(train().level(), carriageTrain(index));
    }

    public double getCarriageHeight(int index) throws LuaException {
        return TrainLength.carHeight(train().level(), carriageTrain(index));
    }

    @LuaFunction(mainThread = true)
    public CarriageHandle getCarriage(int index) throws LuaException {
        requireCarriage(train(), index);
        return new CarriageHandle(this, index);
    }

    @LuaFunction(mainThread = true)
    public void setFrequency(String slot, String itemId1, String itemId2) throws LuaException {
        ServerLevel level = (ServerLevel) axle.getLevel();
        if (level == null) throw new LuaException("no level");
        FrequencySlot existing = frequencyLinks.remove(slot);
        if (existing != null) {
            existing.receiver().removeFromNetwork();
            existing.transmitter().removeFromNetwork();
        }
        ItemStack first = frequencyItem(itemId1);
        ItemStack second = frequencyItem(itemId2);
        CcFrequencyLink receiver = new CcFrequencyLink(axle, level, first, second, true);
        CcFrequencyLink transmitter = new CcFrequencyLink(axle, level, first, second, false);
        receiver.addToNetwork();
        transmitter.addToNetwork();
        frequencyLinks.put(slot, new FrequencySlot(receiver, transmitter));
    }

    @LuaFunction(mainThread = true)
    public int getFrequencyState(String slot) throws LuaException {
        return slot(slot).receiver().getReceivedStrength();
    }

    @LuaFunction(mainThread = true)
    public void setFrequencyState(String slot, int strength) throws LuaException {
        if (strength < 0 || strength > 15) throw new LuaException("strength must be 0 to 15");
        slot(slot).transmitter().setTransmitStrength(strength);
    }

    @LuaFunction(mainThread = true)
    public void clearFrequency(String slot) throws LuaException {
        FrequencySlot removed = frequencyLinks.remove(slot);
        if (removed == null) return;
        removed.receiver().removeFromNetwork();
        removed.transmitter().removeFromNetwork();
    }

    @LuaFunction(mainThread = true)
    public boolean getLeftLink() {
        return axle.isLeftLink();
    }

    @LuaFunction(mainThread = true)
    public boolean getRightLink() {
        return axle.isRightLink();
    }

    @LuaFunction(mainThread = true)
    public boolean getLeftRedstone() {
        return axle.isLeftRedstone();
    }

    @LuaFunction(mainThread = true)
    public boolean getRightRedstone() {
        return axle.isRightRedstone();
    }

    private record FrequencySlot(CcFrequencyLink receiver, CcFrequencyLink transmitter) {}

    private FrequencySlot slot(String name) throws LuaException {
        FrequencySlot found = frequencyLinks.get(name);
        if (found == null) {
            throw new LuaException("slot \"" + name + "\" has no frequency bound, call setFrequency first");
        }
        return found;
    }

    private static ItemStack frequencyItem(String id) throws LuaException {
        net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) {
            throw new LuaException("unknown item id \"" + id + "\"");
        }
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key));
    }

    private int countItems(SableTrain train, int carriageIndex, String filterId) {
        return ConsistAccess.countItems(train.level(), train.car().subLevelId(), carriageIndex, stack -> {
            if (filterId == null) return true;
            return net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString().equals(filterId);
        });
    }

    private int countFluid(SableTrain train, int carriageIndex, String filterId) {
        return ConsistAccess.countFluid(train.level(), train.car().subLevelId(), carriageIndex, stack -> {
            if (filterId == null) return true;
            return net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES
                    .getKey(stack.getFluid().getFluidType()).toString().equals(filterId);
        });
    }

    private Map<String, Integer> collectItems(SableTrain train, int carriageIndex) {
        Map<String, Integer> out = new HashMap<>();
        for (IItemHandler h : ConsistAccess.itemsIn(train.level(), train.car().subLevelId(), carriageIndex)) {
            for (int slot = 0; slot < h.getSlots(); slot++) {
                var stack = h.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString();
                out.merge(id, stack.getCount(), Integer::sum);
            }
        }
        return out;
    }

    private Map<String, Map<String, Integer>> collectFluids(SableTrain train, int carriageIndex) {
        return CarriageStorage.tallyFluids(
                ConsistAccess.fluidsIn(train.level(), train.car().subLevelId(), carriageIndex));
    }

    // every route below is walked from the nose point, so the graph has to be the nose carriages and not this
    private TrackGraph noseGraph(SableTrain train) throws LuaException {
        var carriage = TrainStations.noseRail(train);
        if (carriage == null)
            throw new LuaException("train has no carriage, is it assembled?");
        TrackGraph graph = carriage.graph();
        if (graph == null)
            throw new LuaException("train is not on a track graph, is it on rails?");
        return graph;
    }

    private TravellingPoint nosePoint(SableTrain train) throws LuaException {
        TravellingPoint nose = TrainStations.nosePoint(train);
        if (nose == null)
            throw new LuaException("could not resolve train nose point");
        if (nose.edge == null)
            throw new LuaException("train nose point has no edge, train may not be fully on track");
        return nose;
    }

    private GlobalStation findStation(String name) throws LuaException {
        SableTrain train = train();
        TrackGraph graph = noseGraph(train);
        for (GlobalStation s : graph.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.STATION))
            if (name.equals(s.name)) return s;
        if (Create.RAILWAYS != null)
            for (TrackGraph g : Create.RAILWAYS.trackNetworks.values()) {
                if (g == graph) continue;
                for (GlobalStation s : g.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.STATION))
                    if (name.equals(s.name))
                        throw new LuaException("station \"" + name + "\" exists but is on a different track graph, the train cannot reach it");
            }
        throw new LuaException("station \"" + name + "\" not found in any track graph");
    }

    private record EdgeTarget(TrackNode a, TrackNode b, TrackGraphLocation location, double miss) {
    }

    private static final double ON_TRACK_REACH = 1.5;
    private static final double CURVE_SAMPLE_STEP = 0.25;

    private Couple<TrackNode> nearestEdgeNodes(TrackGraph graph, double x, double y, double z) throws LuaException {
        EdgeTarget target = nearestEdgeTarget(graph, x, y, z);
        return Couple.create(target.a(), target.b());
    }

    private EdgeTarget nearestEdgeTarget(TrackGraph graph, double x, double y, double z) throws LuaException {
        Vec3 target = new Vec3(x, y, z);
        double bestDist = Double.MAX_VALUE;
        TrackNode bestA = null;
        TrackNode bestB = null;
        double bestPositionFromA = 0.0;
        for (TrackNodeLocation locA : graph.getNodes()) {
            TrackNode nodeA = graph.locateNode(locA);
            if (nodeA == null) continue;
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(nodeA);
            if (connections == null) continue;
            for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                TrackEdge edge = entry.getValue();
                if (edge.isInterDimensional()) continue;
                double[] hit = edge.isTurn()
                        ? nearestOnCurve(graph, edge, target)
                        : nearestOnStraight(locA.getLocation(), entry.getKey().getLocation().getLocation(),
                                edge.getLength(), target);
                if (hit[1] < bestDist) {
                    bestDist = hit[1];
                    bestA = nodeA;
                    bestB = entry.getKey();
                    bestPositionFromA = hit[0];
                }
            }
        }
        if (bestA == null)
            throw new LuaException("no track edges found in this graph");
        TrackGraphLocation loc = new TrackGraphLocation();
        loc.graph = graph;
        loc.edge = Couple.create(bestA.getLocation(), bestB.getLocation());
        loc.position = bestPositionFromA;
        return new EdgeTarget(bestA, bestB, loc, bestDist);
    }

    private static double[] nearestOnStraight(Vec3 posA, Vec3 posB, double length, Vec3 target) {
        Vec3 segment = posB.subtract(posA);
        double segLenSq = segment.lengthSqr();
        double t = segLenSq < 1.0e-9 ? 0.0
                : Math.max(0.0, Math.min(1.0, target.subtract(posA).dot(segment) / segLenSq));
        return new double[] { t * length, posA.add(segment.scale(t)).distanceTo(target) };
    }

    private static double[] nearestOnCurve(TrackGraph graph, TrackEdge edge, Vec3 target) {
        double length = edge.getLength();
        double t = 0.0;
        double along = 0.0;
        double[] best = { 0.0, edge.getPosition(graph, 0.0).distanceTo(target) };
        while (along < length) {
            double step = Math.min(CURVE_SAMPLE_STEP, length - along);
            t = Math.min(1.0, edge.incrementT(t, step));
            along += step;
            double d = edge.getPosition(graph, t).distanceTo(target);
            if (d < best[1]) {
                best[0] = along;
                best[1] = d;
            }
        }
        return best;
    }

    private Double pathDistance(SableTrain train, TrackGraphLocation target) throws LuaException {
        TrackGraph graph = noseGraph(train);
        var nose = nosePoint(train);
        TrackNode targetA = graph.locateNode(target.edge.getFirst());
        TrackNode targetB = graph.locateNode(target.edge.getSecond());
        if (targetA == null || targetB == null)
            throw new LuaException("target is on a rail that is not part of this train's track graph");
        if (RouteDistance.onTargetEdge(nose, target)) {
            double signed = RouteDistance.signedOnEdge(nose, target);
            if (signed * SableTrainDriver.noseSense(train, nose) >= 0.0) {
                return Math.abs(signed);
            }
            throw new LuaException("no route ahead of the train, the target is behind it on the same rail");
        }
        var path = SchedulePathfinder.findAhead(graph, nose, targetA, targetB,
                SableTrainDriver.noseSense(train, nose));
        if (path == null)
            throw new LuaException("no route ahead of the train, it may be facing away or on a disconnected segment");
        RouteDistance.Measure measure = RouteDistance.measure(nose, graph, path.edges(), target);
        if (!measure.known())
            throw new LuaException("could not measure distance along the route: " + measure.reason());
        return measure.distance();
    }

    private List<String> stepsTo(SableTrain train, TrackNode targetA, TrackNode targetB) throws LuaException {
        var graph = noseGraph(train);
        var nose = nosePoint(train);
        List<String> steps = new ArrayList<>();
        boolean sameRail = (nose.node1 == targetA && nose.node2 == targetB)
                || (nose.node1 == targetB && nose.node2 == targetA);
        if (sameRail) {
            return steps;
        }
        var path = SchedulePathfinder.findAhead(graph, nose, targetA, targetB,
                SableTrainDriver.noseSense(train, nose));
        if (path == null)
            throw new LuaException("no route ahead of the train, it may be facing away or on a disconnected segment");

        List<Couple<TrackNode>> edges = path.edges();

        for (int i = 0; i < edges.size() - 1; i++) {
            TrackNode pivot = edges.get(i).getSecond();
            Map<TrackNode, ?> branches = graph.getConnectionsFrom(pivot);
            if (branches == null || branches.size() <= 2) continue;

            Vec3 entryDir = hopDir(edges.get(i));
            Vec3 exitDir  = hopDir(edges.get(i + 1));
            if (entryDir == null || exitDir == null) continue;

            double cross = entryDir.x * exitDir.z - entryDir.z * exitDir.x;
            steps.add(Math.abs(cross) < 0.3 ? "straight" : (cross > 0 ? "right" : "left"));
        }

        return steps;
    }

    private Couple<TrackNode> stationNodes(SableTrain train, GlobalStation target) throws LuaException {
        TrackGraph graph = noseGraph(train);
        TrackNode first = graph.locateNode(target.edgeLocation.getFirst());
        TrackNode second = graph.locateNode(target.edgeLocation.getSecond());
        if (first == null || second == null) {
            throw new LuaException("station \"" + target.name
                    + "\" sits on a rail that is not part of this train's track graph");
        }
        return Couple.create(first, second);
    }

    private Vec3 hopDir(Couple<TrackNode> hop) {
        Vec3 from = hop.getFirst().getLocation().getLocation();
        Vec3 to   = hop.getSecond().getLocation().getLocation();
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        return len < 1.0e-6 ? null : new Vec3(dx / len, 0, dz / len);
    }

    private ServerSubLevel requireCarriage(SableTrain train, int index) throws LuaException {
        ServerSubLevel sub = ConsistAccess.carriage(train.level(), train.car().subLevelId(), index - 1);
        if (sub == null) {
            int count = ConsistAccess.carriages(train.level(), train.car().subLevelId()).size();
            throw new LuaException("carriage " + index + " not found, this train has " + count
                    + (count == 1 ? " carriage" : " carriages"));
        }
        return sub;
    }

    private SableScheduleRuntime running(SableTrain train) throws LuaException {
        SableScheduleRuntime rt = schedule(train);
        if (rt == null) {
            throw new LuaException("this train has no schedule, call setSchedule first");
        }
        return rt;
    }

    private CcControl control(SableTrain train) {
        claimedTrain = train.id();
        return CcControl.claim(train.id());
    }

    private static CcControl held(SableTrain train) throws LuaException {
        CcControl control = CcControl.get(train.id());
        if (control == null) {
            throw new LuaException("manual control is not enabled, call setManualControl(true) first");
        }
        return control;
    }
}