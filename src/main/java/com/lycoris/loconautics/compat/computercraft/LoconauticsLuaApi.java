package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.common.railway.PointOverrides;
import com.lycoris.loconautics.common.railway.SignalOverrides;
import com.lycoris.loconautics.common.turntable.Turntable;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.station.StationOccupancy;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.station.GlobalStation;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.NotAttachedException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Glob;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LoconauticsLuaApi implements ILuaAPI, CcEvents.Target {

    private final IComputerSystem computer;
    private record HeldSignal(UUID id, boolean primary) {
    }

    private LoconauticsTrainPeripheral lastHandle;
    private boolean watchingNetwork;
    private final Set<LoconauticsTrainPeripheral> handles = new LinkedHashSet<>();
    private final Set<UUID> stoppedTrains = new HashSet<>();
    private final Set<HeldSignal> heldSignals = new HashSet<>();
    private final Set<String> setPoints = new HashSet<>();

    public LoconauticsLuaApi(IComputerSystem computer) {
        this.computer = computer;
    }

    @Override
    public String[] getNames() {
        return new String[] { "loconautics" };
    }

    @Override
    public String getModuleName() {
        return "loconautics.train";
    }

    @LuaFunction(mainThread = true)
    public final LoconauticsTrainPeripheral getTrain() throws LuaException {
        ServerLevel level = computer.getLevel();
        if (!(Sable.HELPER.getContaining(level, computer.getPosition()) instanceof ServerSubLevel sub)
                || sub.isRemoved()) {
            throw new LuaException("this computer is not inside a train, place it on an assembled carriage");
        }
        BearingAxleBlockEntity axle = BearingAxleValidity.resolve(sub);
        if (axle == null) {
            axle = axleInConsist(level, sub);
        }
        if (axle == null) {
            throw new LuaException("no bearing axle found in this train, the carriage needs one to be driven");
        }
        LoconauticsTrainPeripheral handle = LoconauticsTrainPeripheral.of(axle);
        handle.requireLive();
        lastHandle = handle;
        handles.add(handle);
        CcEvents.register(this);
        return handle;
    }

    @LuaFunction(mainThread = true)
    public final TransmissionPeripheral getTransmission() throws LuaException {
        ServerLevel level = computer.getLevel();
        if (!(Sable.HELPER.getContaining(level, computer.getPosition()) instanceof ServerSubLevel sub)
                || sub.isRemoved()) {
            throw new LuaException("this computer is not inside a train, place it on an assembled carriage");
        }
        TransmissionBlockEntity transmission = transmissionInConsist(level, sub);
        if (transmission == null) {
            throw new LuaException("no transmission found in this train, place one on the shaft end of the bearing axle");
        }
        return TransmissionPeripheral.of(transmission);
    }

    @Override
    public UUID watchedTrain() {
        return lastHandle == null ? null : lastHandle.trainIdOrNull();
    }

    @Override
    public boolean watches(UUID trainId) {
        for (LoconauticsTrainPeripheral handle : handles) {
            if (trainId.equals(handle.trainIdOrNull())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean watchesNetwork() {
        return watchingNetwork;
    }

    @Override
    public void queue(String event, Object[] arguments) {
        try {
            computer.queueEvent(event, arguments);
        } catch (NotAttachedException gone) {
            CcEvents.unregister(this);
        }
    }

    // this api hands out a handle without cc ever attaching a peripheral, so nothing else would give the train back when the computer stops
    @Override
    public void shutdown() {
        watchingNetwork = false;
        CcEvents.unregister(this);
        for (LoconauticsTrainPeripheral handle : handles) {
            handle.releaseEverything();
        }
        handles.clear();
        lastHandle = null;
        releaseOwned();
    }

    private int releaseOwned() {
        int released = stoppedTrains.size();
        for (UUID id : stoppedTrains) {
            CcControl.release(id);
        }
        stoppedTrains.clear();
        for (HeldSignal held : heldSignals) {
            SignalOverrides.release(held.id(), held.primary());
        }
        heldSignals.clear();
        for (String junction : setPoints) {
            PointOverrides.clear(junction);
        }
        setPoints.clear();
        return released;
    }

    private static void requireRemote() throws LuaException {
        if (!LoconauticsConfig.CC_REMOTE_CONTROL.get()) {
            throw new LuaException("remote control is switched off, set compat.computercraft.remoteControl in the server config");
        }
    }

    private static UUID parseId(String text, String what) throws LuaException {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException bad) {
            throw new LuaException("that is not a " + what + " id, read them from the lists the loconautics calls return");
        }
    }

    @LuaFunction(mainThread = true)
    public final LoconauticsTrainPeripheral getTrainById(String id) throws LuaException {
        requireRemote();
        SableTrain train = SableTrainRegistry.get(parseId(id, "train"));
        ServerSubLevel sub = train == null || train.car() == null ? null
                : SubLevelData.resolve(train.level(), train.car().subLevelId());
        if (sub == null) {
            throw new LuaException("no loaded train has that id, list them with getTrains");
        }
        BearingAxleBlockEntity axle = BearingAxleValidity.resolve(sub);
        if (axle == null) {
            axle = axleInConsist(train.level(), sub);
        }
        if (axle == null) {
            throw new LuaException("that train has no bearing axle, it cannot be driven");
        }
        LoconauticsTrainPeripheral handle = LoconauticsTrainPeripheral.of(axle);
        handle.requireLive();
        handles.add(handle);
        CcEvents.register(this);
        return handle;
    }

    @LuaFunction(mainThread = true)
    public final TurntableBearingPeripheral getTurntableById(String id) throws LuaException {
        requireRemote();
        Turntable turntable = TurntableRegistry.byPlate(parseId(id, "turntable"));
        if (!(turntable instanceof TurntableBearingBlockEntity bearing)) {
            throw new LuaException("no assembled turntable has that id, list them with getTurntables");
        }
        return TurntableBearingPeripheral.of(bearing);
    }

    @LuaFunction(mainThread = true)
    public final int stopTrains(IArguments args) throws LuaException {
        requireRemote();
        String regex = Glob.toRegexPattern(args.optString(0).orElse("*"), "");
        boolean emergency = args.optBoolean(1).orElse(false);
        int stopped = 0;
        for (SableTrain train : CcNetwork.fleet().trains()) {
            if (!CcNetwork.trainName(train).matches(regex)) {
                continue;
            }
            CcControl.claim(train.id()).stopAt(train, emergency, Double.NaN);
            stoppedTrains.add(train.id());
            stopped++;
        }
        return stopped;
    }

    @LuaFunction(mainThread = true)
    public final int releaseTrains() {
        return releaseOwned();
    }

    @LuaFunction(mainThread = true)
    public final void holdSignal(IArguments args) throws LuaException {
        requireRemote();
        setHeld(args, true);
    }

    @LuaFunction(mainThread = true)
    public final void releaseSignal(IArguments args) throws LuaException {
        requireRemote();
        setHeld(args, false);
    }

    private void setHeld(IArguments args, boolean hold) throws LuaException {
        UUID id = parseId(args.getString(0), "signal");
        SignalBoundary signal = CcNetwork.findSignal(id);
        if (signal == null) {
            throw new LuaException("no signal has that id, list them with getSignals");
        }
        int side = args.optInt(1).orElse(0);
        if (side < 0 || side > 2) {
            throw new LuaException("side must be 1 or 2, or left out for both");
        }
        for (boolean primary : new boolean[] { true, false }) {
            if ((side == 1 && !primary) || (side == 2 && primary) || signal.blockEntities.get(primary).isEmpty()) {
                continue;
            }
            if (hold) {
                SignalOverrides.hold(id, primary);
                heldSignals.add(new HeldSignal(id, primary));
            } else {
                SignalOverrides.release(id, primary);
                heldSignals.remove(new HeldSignal(id, primary));
            }
        }
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getHeldSignals() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : SignalOverrides.all()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", key.substring(0, key.indexOf('#')));
            entry.put("side", key.endsWith("#1") ? 1 : 2);
            out.add(entry);
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final void setPoint(IArguments args) throws LuaException {
        requireRemote();
        String junction = args.getString(0);
        String toward = args.getString(1);
        CcNetwork.requireJunctionTo(junction, toward);
        PointOverrides.set(junction, toward);
        setPoints.add(junction);
    }

    @LuaFunction(mainThread = true)
    public final void clearPoint(String junction) throws LuaException {
        requireRemote();
        PointOverrides.clear(junction);
        setPoints.remove(junction);
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getPoints() {
        List<Map<String, Object>> out = new ArrayList<>();
        PointOverrides.all().forEach((junction, toward) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("junction", junction);
            entry.put("toward", toward);
            out.add(entry);
        });
        return out;
    }

    @LuaFunction(mainThread = true)
    public final void watchNetwork() {
        watchingNetwork = true;
        CcEvents.register(this);
    }

    @LuaFunction(mainThread = true)
    public final void unwatchNetwork() {
        watchingNetwork = false;
        if (handles.isEmpty()) {
            CcEvents.unregister(this);
        }
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getTrains(IArguments args) throws LuaException {
        return CcNetwork.trains(args.optString(0).orElse("*"));
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getSignals(IArguments args) throws LuaException {
        return CcNetwork.signals(args);
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getBlocks(IArguments args) throws LuaException {
        return CcNetwork.blocks(args.optBoolean(0).orElse(false));
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getJunctions(IArguments args) throws LuaException {
        return CcNetwork.junctions(args);
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getTurntables(IArguments args) throws LuaException {
        return CcNetwork.turntables(args);
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getTrack(IArguments args) throws LuaException {
        return CcNetwork.track(args);
    }

    @LuaFunction(mainThread = true)
    public final boolean isInSubLevel() {
        return Sable.HELPER.getContaining(computer.getLevel(), computer.getPosition()) != null;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getStations(IArguments args) throws LuaException {
        String filter = args.optString(0).orElse("*");
        String regex = Glob.toRegexPattern(filter, "");
        Map<UUID, Map<String, Object>> found = new LinkedHashMap<>();
        if (Create.RAILWAYS == null) {
            return new ArrayList<>();
        }
        for (TrackGraph graph : new ArrayList<>(Create.RAILWAYS.trackNetworks.values())) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                if (station.name == null || !station.name.matches(regex) || found.containsKey(station.id)) {
                    continue;
                }
                SableTrain parked = StationOccupancy.parkedAt(station, null);
                SableTrain heading = SableScheduleRuntime.headingTo(station, null);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", station.name);
                entry.put("id", station.id.toString());
            CcNetwork.addPosition(entry, station.blockEntityDimension, station.blockEntityPos);
                entry.put("free", parked == null && heading == null);
                if (parked != null) {
                    entry.put("parkedTrain", parked.id().toString());
                }
                if (heading != null) {
                    entry.put("claimedBy", heading.id().toString());
                }
                found.put(station.id, entry);
            }
        }
        return new ArrayList<>(found.values());
    }

    private static TransmissionBlockEntity transmissionInConsist(ServerLevel level, ServerSubLevel start) {
        TransmissionBlockEntity own = SubLevelBlocks.find(start, TransmissionBlockEntity.class);
        if (own != null) {
            return own;
        }
        for (UUID id : TrainConsist.connectedSubLevels(level, start.getUniqueId())) {
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            TransmissionBlockEntity found = sub == null ? null : SubLevelBlocks.find(sub, TransmissionBlockEntity.class);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static BearingAxleBlockEntity axleInConsist(ServerLevel level, ServerSubLevel start) {
        for (UUID id : TrainConsist.connectedSubLevels(level, start.getUniqueId())) {
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            if (sub == null) {
                continue;
            }
            BearingAxleBlockEntity axle = BearingAxleValidity.resolve(sub);
            if (axle != null) {
                return axle;
            }
        }
        return null;
    }
}
