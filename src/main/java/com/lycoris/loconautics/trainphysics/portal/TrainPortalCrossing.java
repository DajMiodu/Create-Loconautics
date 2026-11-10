package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.teleport.TrainTeleporter;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrainPortalCrossing {

    private static final double HANDOVER_DEPTH = 0.25;

    private static final double CLEAR_MARGIN = 1.0;

    private static final long RETRY_TICKS = 40;

    private static final Map<UUID, PortalFrame> STRADDLING = new HashMap<>();

    private static final Map<UUID, Long> RETRY_AT = new HashMap<>();

    private static final Map<UUID, Integer> CLEAR_TICKS = new HashMap<>();

    private static final int CLEAR_HOLD = 20;

    private TrainPortalCrossing() {
    }

    public static Set<UUID> entering() {
        return Set.copyOf(STRADDLING.keySet());
    }

    public static boolean isCrossing(UUID subLevelId) {
        return SableTeleportation.isInFlight(subLevelId);
    }

    public static PortalFrame straddle(UUID carSubLevelId) {
        return STRADDLING.get(carSubLevelId);
    }

    public static void tick(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car == null) {
            return;
        }
        PortalFrame frame = unifyFrame(car, train);
        if (frame == null) {
            end(train, false);
            return;
        }
        ServerLevel home = train.level();
        ServerSubLevel master = SubLevelData.resolve(home, car.subLevelId());
        if (master == null) {
            return;
        }
        double[] span = span(master, frame);
        if (span == null) {
            return;
        }
        if (!TrainRailAdvance.anyPointFar(car) && span[1] <= -CLEAR_MARGIN) {
            int clear = CLEAR_TICKS.merge(car.subLevelId(), 1, Integer::sum);
            if (clear >= CLEAR_HOLD) {
                CLEAR_TICKS.remove(car.subLevelId());
                end(train, true);
                return;
            }
        } else {
            CLEAR_TICKS.remove(car.subLevelId());
        }
        STRADDLING.put(car.subLevelId(), frame);
        PortalHold.hold(home, car.subLevelId());
        // twins first, a handover on the first straddle tick would otherwise skip the standby and pop the whole car over
        straddle(train, car, master, frame);
        if (handOver(train, frame)) {
            return;
        }
    }

    private static void straddle(SableTrain train, SableTrain.Car car, ServerSubLevel master, PortalFrame frame) {
        MinecraftServer server = train.level().getServer();
        ServerLevel farLevel = server.getLevel(frame.farDim());
        Vector3d farCenter = frame.toFar(master.logicalPose().position());
        if (farLevel != null && !PortalHold.holdFarChunks(farLevel, farCenter.x, farCenter.y, farCenter.z)) {
            return;
        }
        Set<UUID> group = new HashSet<>();
        for (SableTrain member : chain(train)) {
            group.add(member.car().subLevelId());
        }
        List<UUID> homeBodies = new ArrayList<>();
        List<UUID> twinBodies = new ArrayList<>();
        List<UUID> standbyBodies = new ArrayList<>();
        homeBodies.add(car.subLevelId());
        ServerSubLevel twin = PortalTwins.ensure(master, frame, car.subLevelId(), group);
        if (twin != null) {
            twinBodies.add(twin.getUniqueId());
        }
        for (UUID plateId : GhostWheelBodies.wheelBodiesOf(car.subLevelId())) {
            ServerSubLevel plate = SubLevelData.resolve(train.level(), plateId);
            if (plate == null) {
                continue;
            }
            homeBodies.add(plateId);
            ServerSubLevel plateTwin = PortalTwins.ensure(plate, frame, car.subLevelId(), group);
            if (plateTwin != null) {
                twinBodies.add(plateTwin.getUniqueId());
            }
        }
        PortalTwins.retain(server, car.subLevelId(), homeBodies);
        UUID standby = PortalTwins.standbyBody(car.subLevelId());
        if (standby != null) {
            standbyBodies.add(standby);
        }
        PortalEntry.sync(train.level(), car.subLevelId(), frame, homeBodies, standbyBodies,
                server.getLevel(frame.farDim()), twinBodies);
    }

    private static boolean handOver(SableTrain train, PortalFrame frame) {
        List<SableTrain> chain = chain(train);
        List<SableTrain> past = new ArrayList<>();
        List<ServerPlayer> riders = new ArrayList<>();
        for (SableTrain member : chain) {
            ServerSubLevel sub = member.level() == train.level()
                    ? SubLevelData.resolve(member.level(), member.car().subLevelId()) : null;
            if (sub == null) {
                return false;
            }
            riders.addAll(TrainAdvancements.riders(sub));
            if (frame.depth(sub.logicalPose().position()) > HANDOVER_DEPTH) {
                past.add(member);
            }
        }
        long now = train.level().getGameTime();
        if (past.size() * 2 <= chain.size()) {
            return false;
        }
        long retryAt = RETRY_AT.getOrDefault(train.car().subLevelId(), 0L);
        if (now < retryAt) {
            return false;
        }
        String refusal = TrainTeleporter.handOverRefusal(train);
        if (refusal != null) {
            return false;
        }
        MinecraftServer server = train.level().getServer();
        boolean waiting = false;
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            waiting |= PortalTwins.standbyPending(SubLevelData.resolve(member.level(), id), id);
        }
        if (waiting) {
            return false;
        }
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            PortalTwins.softenTwin(server, id);
            PortalHold.release(server, id);
        }
        TrainTeleporter.Result result = TrainTeleporter.handOver(train, frame);
        if (!result.ok()) {
            LoconauticsConstants.LOGGER.warn("[portal] handover failed car={} chain={} moved={} why={}",
                    train.car().subLevelId(), chain.size(), result.moved(), result.failure());
            RETRY_AT.put(train.car().subLevelId(), now + RETRY_TICKS);
            return false;
        }
        PortalFrame turned = frame.inverted();
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            PortalEntry.clear(id, PortalTwins.handedOver(server, id, turned));
            STRADDLING.remove(id);
        }
        RETRY_AT.remove(train.car().subLevelId());
        TrainAdvancements.portal(riders);
        return true;
    }

    // a body overhangs its bogeys, so the straddle has to start when its front nears the plane, not when a bogey does
    private static PortalFrame lookAhead(SableTrain.Car car, SableTrain train) {
        ServerSubLevel master = SubLevelData.resolve(train.level(), car.subLevelId());
        BoundingBox3ic bounds = master == null ? null : master.getPlot().getBoundingBox();
        if (car.carriage() == null || bounds == null || bounds.volume() <= 0) {
            return null;
        }
        double reach = Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) + 1.0;
        PortalFrame ahead = car.carriage().portalWithin(reach);
        double[] span = ahead == null ? null : span(master, ahead);
        if (span == null || span[1] <= -CLEAR_MARGIN) {
            return null;
        }
        return ahead;
    }

    private static PortalFrame unifyFrame(SableTrain.Car car, SableTrain train) {
        PortalFrame frame = rails(car).stream().map(RailCarriage::portal)
                .filter(own -> own != null && own.nearDim().equals(train.level().dimension()))
                .findFirst().orElse(null);
        if (frame == null) {
            frame = lookAhead(car, train);
        }
        if (frame == null) {
            return null;
        }
        for (RailCarriage rail : rails(car)) {
            if (rail.portal() != frame) {
                rail.setPortal(frame);
            }
        }
        return frame;
    }

    private static void end(SableTrain train, boolean clearRails) {
        SableTrain.Car car = train.car();
        UUID id = car.subLevelId();
        if (clearRails) {
            for (RailCarriage rail : rails(car)) {
                rail.clearPortal();
            }
        }
        RETRY_AT.remove(id);
        CLEAR_TICKS.remove(id);
        if (STRADDLING.remove(id) == null) {
            return;
        }
        MinecraftServer server = train.level().getServer();
        PortalTwins.endStraddle(server, id);
        PortalEntry.clear(id);
        PortalHold.release(server, id);
    }

    private static List<RailCarriage> rails(SableTrain.Car car) {
        List<RailCarriage> rails = new ArrayList<>();
        if (car.carriage() != null) {
            rails.add(car.carriage());
        }
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (bogey.rail() != null) {
                rails.add(bogey.rail());
            }
        }
        return rails;
    }

    private static List<SableTrain> chain(SableTrain train) {
        List<SableTrain> cars = SableTrainSpawner.chainCars(train.level(), train.car().subLevelId());
        return cars.isEmpty() ? List.of(train) : cars;
    }

    private static double[] span(ServerSubLevel sub, PortalFrame frame) {
        BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
        if (bounds == null || bounds.volume() <= 0) {
            return null;
        }
        Pose3dc pose = sub.logicalPose();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        Vector3d corner = new Vector3d();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? bounds.minX() : bounds.maxX() + 1.0,
                    (i & 2) == 0 ? bounds.minY() : bounds.maxY() + 1.0,
                    (i & 4) == 0 ? bounds.minZ() : bounds.maxZ() + 1.0);
            pose.transformPosition(corner);
            double depth = frame.depth(corner);
            min = Math.min(min, depth);
            max = Math.max(max, depth);
        }
        return new double[] {min, max};
    }
}
