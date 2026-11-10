package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.WheelSlip;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class ConsistPower {

    // powered means plumbed into a kinetic network. an axle with nothing on its shaft carries no load and would dilute everyone else
    public record Axle(UUID subLevelId, BlockPos axleLocalPos, BlockPos bogeyLocalPos,
                       double strength, double signedStrength, int group, double railSpeed,
                       boolean powered) {}

    public record Result(double netStrength, double forwardStrength, double reverseStrength,
                         double slipFactor, double targetSpeedBpt, double gearedTopBpt,
                         int forwardCount, int reverseCount,
                         double totalMass, double brakeLoad, int memberCount, List<Axle> axles) {

        Result withDrive(double target, double gearedTop) {
            return new Result(netStrength, forwardStrength, reverseStrength, slipFactor, target, gearedTop,
                    forwardCount, reverseCount, totalMass, brakeLoad, memberCount, axles);
        }

        public boolean anyPowered() {
            return forwardCount > 0 || reverseCount > 0;
        }

        // the same test manualdrive gets as engineturning, an axle with rpm after gearing and stress
        public boolean engineTurning() {
            return axles.stream().anyMatch(axle -> axle.strength() > 0.0);
        }

        public int groupSize(int group) {
            return group < 0 ? reverseCount : forwardCount;
        }

        public double share(int group) {
            int size = groupSize(group);
            return size <= 0 ? 0.0 : (totalMass + brakeLoad) / size;
        }

        public boolean isMinority(int group) {
            if (slipFactor >= 1.0) {
                return false;
            }
            double own = group < 0 ? reverseStrength : forwardStrength;
            double other = group < 0 ? forwardStrength : reverseStrength;
            return own <= other;
        }
    }

    private static final Map<UUID, Result> BY_SUB_LEVEL = new HashMap<>();
    private static final Map<UUID, Double> MASS_CACHE = new HashMap<>();
    private static final Map<Long, Boolean> OVERSTRESSED_SEEN = new HashMap<>();
    private static final Map<UUID, Double> TARGETS = new HashMap<>();
    private static final Set<UUID> MANUAL = new HashSet<>();
    private static final Map<UUID, Double> PEAK_TOP = new HashMap<>();
    private static final Map<UUID, Double> DRIVER_BOOST = new HashMap<>();
    private static final Map<UUID, String> LAST_PULL = new HashMap<>();

    private ConsistPower() {
    }

    @Nullable
    public static Result resultFor(SableTrain train) {
        if (train.car() == null || train.car().subLevelId() == null) {
            return null;
        }
        return BY_SUB_LEVEL.get(train.car().subLevelId());
    }

    public static double consistMass(SableTrain train, double fallback) {
        if (train.car() == null || train.car().subLevelId() == null) {
            return fallback;
        }
        Double mass = MASS_CACHE.get(train.car().subLevelId());
        return mass == null || mass <= 0.0 ? fallback : mass;
    }

    public static double tractionMass(SableTrain train, double fallback) {
        double mass = consistMass(train, fallback);
        Result result = resultFor(train);
        if (result == null) {
            return mass;
        }
        int pulling = result.forwardStrength() >= result.reverseStrength() ? result.forwardCount() : result.reverseCount();
        return mass / Math.max(1, pulling);
    }

    public static double targetFor(SableTrain train) {
        Double target = TARGETS.get(train.car().subLevelId());
        return target == null ? 0.0 : target;
    }

    public static double peakTopBpt(SableTrain train) {
        if (train.car() == null || train.car().subLevelId() == null) {
            return 0.0;
        }
        return PEAK_TOP.getOrDefault(train.car().subLevelId(), 0.0);
    }

    public static double driverBoost(SableTrain train) {
        return train.car() == null ? 1.0 : DRIVER_BOOST.getOrDefault(train.car().subLevelId(), 1.0);
    }

    public static boolean isManual(SableTrain train) {
        return train.car() != null && MANUAL.contains(train.car().subLevelId());
    }

    public static void clear() {
        BY_SUB_LEVEL.clear();
        MASS_CACHE.clear();
        TARGETS.clear();
        MANUAL.clear();
        PEAK_TOP.clear();
        DRIVER_BOOST.clear();
        LAST_PULL.clear();
        BearingAxleValidity.clearCache();
    }

    public static void tick(boolean refreshMass) {
        BY_SUB_LEVEL.clear();
        TARGETS.clear();
        MANUAL.clear();
        DRIVER_BOOST.clear();
        BearingAxleValidity.clearCache();
        Map<UUID, SableTrain> bySubLevel = new HashMap<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.car() != null && train.car().subLevelId() != null) {
                bySubLevel.put(train.car().subLevelId(), train);
            }
        }
        if (refreshMass) {
            MASS_CACHE.clear();
        }
        Set<UUID> done = new HashSet<>();
        for (SableTrain seed : SableTrainRegistry.all()) {
            if (seed.car() == null || seed.car().subLevelId() == null
                    || !done.add(seed.car().subLevelId())) {
                continue;
            }
            solveConsist(seed, bySubLevel, done, refreshMass);
        }
        PEAK_TOP.keySet().retainAll(done);
    }

    private static void solveConsist(SableTrain seed, Map<UUID, SableTrain> bySubLevel,
                                     Set<UUID> done, boolean refreshMass) {
        ServerLevel level = seed.level();
        UUID seedId = seed.car().subLevelId();
        List<UUID> members = new ArrayList<>();
        for (UUID id : TrainConsist.connectedSubLevels(level, seedId)) {
            SableTrain member = bySubLevel.get(id);
            if (member != null && member.level() == level) {
                members.add(id);
                done.add(id);
            }
        }
        if (members.isEmpty()) {
            members.add(seedId);
        }

        Map<UUID, Integer> orientation = orientations(level, seedId, members, bySubLevel);
        List<Axle> axles = new ArrayList<>();
        List<BearingAxleBlockEntity> entities = new ArrayList<>();
        Map<UUID, Map<BlockPos, Double>> driven = new LinkedHashMap<>();
        double forward = 0.0;
        double reverse = 0.0;
        double brakeLoad = 0.0;

        for (UUID id : members) {
            SableTrain member = bySubLevel.get(id);
            if (member == null || member.isGhost() || member.isDerailed() || member.isPhysics()) {
                continue;
            }
            brakeLoad += TrainBrakes.handbrakeLoadMass(member);
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            if (sub == null || sub.isRemoved()) {
                continue;
            }
            int flip = orientation.getOrDefault(id, 1);
            Map<BlockPos, Double> carDriven = new LinkedHashMap<>();
            double carSpeed = WheelSlip.carSpeed(member, sub);
            List<BearingAxleBlockEntity> working = new ArrayList<>();
            double carSigned = 0.0;
            for (BearingAxleBlockEntity axle : BearingAxleValidity.valid(sub)) {
                Axle entry = readAxle(member, sub, axle, flip);
                if (entry == null) {
                    axle.setSlopeAngle(0.0);
                    axle.resetGearing();
                    continue;
                }
                axles.add(entry);
                entities.add(axle);
                if (entry.powered()) {
                    working.add(axle);
                } else {
                    axle.setSlopeAngle(0.0);
                }
                carSigned += entry.signedStrength();
                carDriven.put(entry.bogeyLocalPos(), WheelSlip.surfaceSpeed(member, sub, entry.bogeyLocalPos(),
                        entry.railSpeed(), carSpeed));
                if (entry.signedStrength() >= 0.0) {
                    forward += entry.signedStrength();
                } else {
                    reverse += -entry.signedStrength();
                }
            }
            applySlope(member, working, carSigned);
            driven.put(id, carDriven);
        }

        int forwardCount = 0;
        int reverseCount = 0;
        int driving = 0;
        for (Axle axle : axles) {
            if (axle.powered()) {
                if (axle.group() < 0) {
                    reverseCount++;
                } else {
                    forwardCount++;
                }
            }
            if (axle.strength() > 0.0) {
                driving++;
            }
        }

        double net = forward - reverse;
        double total = forward + reverse;
        double slip = total <= 0.0 ? 1.0 : Math.abs(net) / total;
        double mass = refreshMass || !MASS_CACHE.containsKey(seedId)
                ? TrainConsist.totalMass(level, seedId)
                : MASS_CACHE.get(seedId);
        MASS_CACHE.put(seedId, mass);

        double baseline = TrainPropulsion.bearingAxleCapBpt();
        Result result = new Result(net, forward, reverse, slip, 0.0, baseline,
                forwardCount, reverseCount, mass, brakeLoad, members.size(), List.copyOf(axles));
        AxleGearing.Gearing[] gearing = gearing(entities, axles, result);
        double ceiling = TrainPropulsion.speedCeilingMps() / 20.0;
        double gearedNet = 0.0;
        double turningRatio = 0.0;
        double anyRatio = 0.0;
        for (int i = 0; i < axles.size(); i++) {
            gearedNet += axles.get(i).signedStrength() * gearing[i].ratio();
            anyRatio += gearing[i].ratio();
            if (axles.get(i).strength() > 0.0) {
                turningRatio += gearing[i].ratio();
            }
        }
        double ratio = driving > 0 ? turningRatio / driving : axles.isEmpty() ? 1.0 : anyRatio / axles.size();
        double gearedTop = Math.min(ceiling, baseline * ratio);
        double perAxle = driving == 0 ? 0.0 : gearedNet / driving;
        double target = Math.max(-ceiling, Math.min(ceiling, AxleGearing.gearedSpeed(baseline, 1.0, perAxle)));
        List<ManualDrive.Driver> drivers = ManualDrive.driversIn(level, members);
        double boost = 1.0;
        if (!drivers.isEmpty()) {
            ManualDrive.Pull pull = ManualDrive.pull(drivers, bySubLevel, orientation, driving > 0, gearedTop,
                    Math.abs(target));
            target = pull.target();
            boost = ManualDrive.accelerationScale(pull.pushers());
            MANUAL.addAll(members);
            tracePull(members, drivers.size(), pull, boost);
        }
        result = result.withDrive(target, gearedTop);

        for (int i = 0; i < entities.size(); i++) {
            Axle entry = axles.get(i);
            int group = entry.group();
            entities.get(i).setConsistLoad(result.share(group), result.isMinority(group), group,
                    entry.signedStrength(), net, slip, result.groupSize(group), gearing[i]);
        }
        double peakFall = TrainPropulsion.brakeDecelLimit() * TrainPropulsion.MPS2_TO_BPT2;
        for (UUID id : members) {
            BY_SUB_LEVEL.put(id, result);
            MASS_CACHE.put(id, mass);
            TARGETS.put(id, target * orientation.getOrDefault(id, 1));
            PEAK_TOP.merge(id, gearedTop, (was, now) -> Math.max(now, was - peakFall));
            if (boost != 1.0) {
                DRIVER_BOOST.put(id, boost);
            }
            SableTrain member = bySubLevel.get(id);
            if (member != null) {
                member.setDrivenBogeys(driven.getOrDefault(id, Map.of()));
                member.setSlipFactor(slip);
            }
        }
    }

    private static void tracePull(List<UUID> members, int drivers, ManualDrive.Pull pull, double boost) {
        UUID key = members.stream().min(UUID::compareTo).orElse(null);
        String line = drivers + " drivers, ahead " + pull.ahead() + " back " + pull.back() + " boost " + boost;
        if (drivers < 2 || key == null || line.equals(LAST_PULL.put(key, line))) {
            return;
        }
        LoconauticsConstants.LOGGER.info("[multidrive] consist {} {} target {}", shortId(key), line,
                String.format("%.3f", pull.target()));
    }

    private static AxleGearing.Gearing[] gearing(List<BearingAxleBlockEntity> entities, List<Axle> axles,
                                                 Result result) {
        boolean enabled = LoconauticsConfig.GEARING_ENABLED.get();
        List<AxleGearing.Load> loads = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            BearingAxleBlockEntity axle = entities.get(i);
            int group = axles.get(i).group();
            loads.add(new AxleGearing.Load(axle.hasNetwork() ? axle.network : null,
                    axle.networkCapacity(), axle.networkStress(), axle.ownNetworkStress(),
                    axle.directImpact(result.share(group), result.isMinority(group)),
                    enabled ? axle.gear() : 0));
        }
        AxleGearing.Gearing[] out = AxleGearing.solve(loads, LoconauticsConfig.GEARING_OVERDRIVE_SCALE.get(),
                TrainPropulsion.gearingCeilingRatio());
        for (int i = 0; i < entities.size(); i++) {
            BearingAxleBlockEntity axle = entities.get(i);
            if (enabled && !axle.hasNetwork() && axle.gear() > 0) {
                out[i] = new AxleGearing.Gearing(AxleGearing.gearRatio(axle.gear(), axle.maxGearRatio()),
                        axle.maxGearRatio());
            }
        }
        return out;
    }

    private static void applySlope(SableTrain train, List<BearingAxleBlockEntity> working, double signedStrength) {
        if (working.isEmpty()) {
            return;
        }
        boolean climbing = train.car().carriage() != null
                && LoconauticsConfig.SLOPE_EFFECTS_ENABLED.get()
                && !TrainPropulsion.isMonorail(train)
                && TrainPropulsion.isClimbing(train, signedStrength);
        double share = climbing ? TrainPropulsion.inclineDegrees(train) / working.size() : 0.0;
        for (BearingAxleBlockEntity axle : working) {
            axle.setSlopeAngle(share);
        }
    }

    @Nullable
    private static Axle readAxle(SableTrain train, ServerSubLevel sub, BearingAxleBlockEntity axle, int flip) {
        BlockPos bogeyWorld = axle.driverBogeyPos();
        if (bogeyWorld == null) {
            return null;
        }
        BlockPos bogeyLocal = bogeyWorld.subtract(sub.getPlot().getCenterBlock());
        if (train.isBogeyOffTrack(bogeyLocal)) {
            return null;
        }
        SableTrain.Bogey bogey = bogeyAt(train, bogeyLocal);
        Direction front = TrainSteering.axleFront(axle, sub);
        if (bogey == null || bogey.rail() == null || front == null) {
            return null;
        }
        Vec3 carForward = TrainRailAdvance.bodyForward(bogey.rail());
        if (carForward == null) {
            return null;
        }
        double alignment = front.getStepX() * carForward.x + front.getStepZ() * carForward.z;
        if (Math.abs(alignment) < 1.0e-6) {
            return null;
        }
        int frontSign = alignment < 0.0 ? -1 : 1;
        if (axle.isOverStressed() != OVERSTRESSED_SEEN.getOrDefault(axle.getBlockPos().asLong(), false)) {
            OVERSTRESSED_SEEN.put(axle.getBlockPos().asLong(), axle.isOverStressed());
            LoconauticsConstants.LOGGER.info("[slipdbg] axle {} overstressed {} speed {} stress {} capacity {}",
                    axle.getBlockPos().toShortString(), axle.isOverStressed(), axle.getSpeed(),
                    axle.networkStress(), axle.networkCapacity());
        }
        double rpm = axle.isOverStressed() ? 0.0 : axle.getSpeed();
        double strength = Math.abs(rpm);
        int facing = frontSign * flip;
        int push = rpm < 0.0 ? -facing : facing;
        double signed = strength * push;
        double railSpeed = AxleGearing.gearedSpeed(TrainPropulsion.bearingAxleCapBpt(), axle.gearRatio(), rpm)
                * frontSign;
        return new Axle(sub.getUniqueId(), axle.getBlockPos().subtract(sub.getPlot().getCenterBlock()),
                bogeyLocal, strength, signed, strength <= 0.0 ? facing : push, railSpeed,
                axle.hasNetwork());
    }

    @Nullable
    private static SableTrain.Bogey bogeyAt(SableTrain train, BlockPos localPos) {
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (bogey.localPos().equals(localPos)) {
                return bogey;
            }
        }
        return null;
    }

    private static Map<UUID, Integer> orientations(ServerLevel level, UUID seedId, List<UUID> members,
                                                   Map<UUID, SableTrain> bySubLevel) {
        Map<UUID, Integer> out = new HashMap<>();
        List<UUID> order = TrainConsist.orderedChain(level, seedId);
        Vec3 previous = null;
        int previousFlip = 1;
        for (UUID id : order) {
            Vec3 forward = forwardOf(bySubLevel.get(id));
            if (forward == null) {
                continue;
            }
            int flip = previous == null ? 1 : (previous.dot(forward) < 0.0 ? -previousFlip : previousFlip);
            out.put(id, flip);
            previous = forward;
            previousFlip = flip;
        }
        Vec3 reference = forwardOf(bySubLevel.get(seedId));
        int seedFlip = out.getOrDefault(seedId, 1);
        for (UUID id : members) {
            if (out.containsKey(id)) {
                continue;
            }
            Vec3 forward = forwardOf(bySubLevel.get(id));
            out.put(id, reference == null || forward == null || reference.dot(forward) >= 0.0
                    ? seedFlip : -seedFlip);
        }
        if (seedFlip != 1) {
            out.replaceAll((id, flip) -> flip * seedFlip);
        }
        return out;
    }

    @Nullable
    static Vec3 forwardOf(@Nullable SableTrain train) {
        if (train == null || train.car() == null || train.car().bogeys().isEmpty()) {
            return null;
        }
        var rail = train.leadingBogeyRail();
        if (rail == null) {
            return null;
        }
        Vec3 forward = TrainRailAdvance.bodyForward(rail);
        return forward == null || forward.lengthSqr() < 1.0e-9 ? null : forward.normalize();
    }
}
