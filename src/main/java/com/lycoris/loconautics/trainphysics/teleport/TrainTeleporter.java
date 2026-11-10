package com.lycoris.loconautics.trainphysics.teleport;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import com.lycoris.loconautics.trainphysics.portal.PortalFrame;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.PlacementClearance;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class TrainTeleporter {

    private TrainTeleporter() {
    }

    public record Result(@Nullable String failure, int moved, List<SableTrain> cars) {
        public boolean ok() {
            return failure == null;
        }
    }

    private record Landing(SableTrain train, ServerSubLevel sub, Vector3d origin, Quaterniond orient,
                           @Nullable SableTrainSpawner.SeatPose seat, double railTravelSign) {}

    private enum Mode { RAIL, FREE, HANDOVER }

    public static Result teleport(SableTrain train, ServerLevel destLevel, Vec3 position, double yawDeg,
                                  Consumer<String> trace) {
        List<SableTrain> cars = chain(train);
        String refusal = refuse(cars);
        if (refusal != null) {
            return new Result(refusal, 0, cars);
        }
        List<Landing> plan = freeDropPlan(cars, position, yawDeg);
        if (plan == null) {
            return new Result("one of the cars has no live body", 0, cars);
        }
        return run(plan, destLevel, Mode.FREE, trace);
    }

    public static Result teleportToRail(SableTrain train, ServerLevel destLevel, TrackGraphLocation loc,
                                        Vec3 upNormal, Vec3 look, Consumer<String> trace) {
        return teleportToRail(train, destLevel, loc, upNormal, look, look, trace);
    }

    public static Result teleportToRail(SableTrain train, ServerLevel destLevel, TrackGraphLocation loc,
                                        Vec3 upNormal, Vec3 look, @Nullable Vec3 facing, Consumer<String> trace) {
        List<SableTrain> cars = chain(train);
        String refusal = refuse(cars);
        if (refusal != null) {
            return new Result(refusal, 0, cars);
        }
        List<TrackGraphLocation> spots = SableTrainSpawner.chainSpots(cars, loc, upNormal, look);
        if (spots == null) {
            return new Result("not enough track past there for the whole train", 0, cars);
        }
        if (PlacementClearance.blocker(destLevel, cars, spots, upNormal) != null) {
            return new Result("another train is on the track there", 0, cars);
        }
        List<Landing> plan = new ArrayList<>();
        for (int i = 0; i < cars.size(); i++) {
            SableTrain car = cars.get(i);
            ServerSubLevel sub = SubLevelData.resolve(car.level(), car.car().subLevelId());
            if (sub == null) {
                return new Result("car " + shortId(car) + " has no live body", 0, cars);
            }
            Vec3 carFacing = facing != null ? facing : (car.speed() < 0.0 ? look.reverse() : look);
            SableTrainSpawner.SeatPose seat =
                    SableTrainSpawner.seatPoseAt(car.level(), car, spots.get(i), upNormal, carFacing, false);
            if (seat == null || seat.origin() == null) {
                return new Result("car " + shortId(car) + " has no usable rail pose there", 0, cars);
            }
            plan.add(new Landing(car, sub, seat.origin(), seat.orient(), seat,
                    carFacing.dot(look) < 0.0 ? -1.0 : 1.0));
        }
        return run(plan, destLevel, Mode.RAIL, trace);
    }

    @Nullable
    public static String handOverRefusal(SableTrain train) {
        return refuse(chain(train));
    }

    public static Result handOver(SableTrain train, PortalFrame frame, Consumer<String> trace) {
        List<SableTrain> cars = chain(train);
        String refusal = refuse(cars);
        if (refusal != null) {
            return new Result(refusal, 0, cars);
        }
        ServerLevel destLevel = train.level().getServer().getLevel(frame.farDim());
        if (destLevel == null) {
            return new Result("no level loaded for " + frame.farDim().location(), 0, cars);
        }
        List<Landing> plan = new ArrayList<>();
        for (SableTrain car : cars) {
            ServerSubLevel sub = SubLevelData.resolve(car.level(), car.car().subLevelId());
            if (sub == null || car.level() != train.level()) {
                return new Result("car " + shortId(car) + " has no live body on this side", 0, cars);
            }
            plan.add(new Landing(car, sub, frame.toFar(sub.logicalPose().position()),
                    frame.orientToFar(sub.logicalPose().orientation()), null, 1.0));
        }
        Result result = run(plan, destLevel, Mode.HANDOVER, trace);
        PortalFrame turned = frame.inverted();
        for (SableTrain car : result.cars()) {
            if (car.level() == destLevel) {
                for (RailCarriage rail : rails(car)) {
                    rail.setPortal(turned);
                }
            }
        }
        return result;
    }

    private static List<RailCarriage> rails(SableTrain train) {
        List<RailCarriage> rails = new ArrayList<>();
        if (train.car().carriage() != null) {
            rails.add(train.car().carriage());
        }
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
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

    @Nullable
    private static String refuse(List<SableTrain> cars) {
        for (SableTrain car : cars) {
            if (car.car() == null || car.car().subLevelId() == null) {
                return "a car in the chain has no body";
            }
            if (car.isPhysics()) {
                return "car " + shortId(car) + " is a free physics body, not a pinned one";
            }
            if (SableTeleportation.isInFlight(car.car().subLevelId())) {
                return "car " + shortId(car) + " is already mid teleport";
            }
        }
        return null;
    }

    @Nullable
    private static List<Landing> freeDropPlan(List<SableTrain> cars, Vec3 position, double yawDeg) {
        List<ServerSubLevel> subs = new ArrayList<>();
        for (SableTrain car : cars) {
            ServerSubLevel sub = SubLevelData.resolve(car.level(), car.car().subLevelId());
            if (sub == null) {
                return null;
            }
            subs.add(sub);
        }
        ServerSubLevel lead = subs.get(0);
        Quaterniond target = new Quaterniond().rotateY(Math.toRadians(yawDeg));
        Quaterniondc leadOrient = lead.logicalPose().orientation();
        Quaterniond turn = new Quaterniond(target).mul(new Quaterniond(leadOrient).invert());
        Vector3d leadOrigin = new Vector3d(lead.logicalPose().position());

        Vector3d landing = new Vector3d(position.x, position.y + SableTeleportation.baseOffsetY(lead), position.z);

        List<Landing> plan = new ArrayList<>();
        for (int i = 0; i < cars.size(); i++) {
            ServerSubLevel sub = subs.get(i);
            Vector3d offset = new Vector3d(sub.logicalPose().position()).sub(leadOrigin);
            turn.transform(offset);
            Vector3d origin = new Vector3d(landing).add(offset);
            Quaterniond orient = new Quaterniond(turn).mul(new Quaterniond(sub.logicalPose().orientation()));
            plan.add(new Landing(cars.get(i), sub, origin, orient, null, 1.0));
        }
        return plan;
    }

    private static Result run(List<Landing> plan, ServerLevel destLevel, Mode mode, Consumer<String> trace) {
        SubLevelSplitWatcher.ensureObserver(destLevel);
        Map<UUID, SableTeleportation.PlotSlot> seats = new HashMap<>();
        Set<SableTeleportation.PlotSlot> claimed = new HashSet<>();
        for (Landing landing : plan) {
            if (landing.sub().getLevel() == destLevel) {
                continue;
            }
            SableTeleportation.PlotSlot slot = SableTeleportation.plotSlot(landing.sub());
            if (slot == null) {
                return new Result("car " + shortId(landing.train()) + " has no plot slot", 0, cars(plan));
            }
            if (claimed.contains(slot) || !SableTeleportation.plotSlotFree(destLevel, slot)) {
                slot = SableTeleportation.firstFreeSlot(destLevel, claimed);
            }
            if (slot == null) {
                return new Result("no free plot left in " + destLevel.dimension().location()
                        + " for car " + shortId(landing.train()), 0, cars(plan));
            }
            claimed.add(slot);
            seats.put(landing.sub().getUniqueId(), slot);
            trace.accept("car " + shortId(landing.train()) + " takes plot " + slot.x() + ", " + slot.z() + " in "
                    + destLevel.dimension().location());
        }

        Set<UUID> moving = new HashSet<>();
        for (Landing landing : plan) {
            moving.add(landing.sub().getUniqueId());
        }

        ServerLevel sourceLevel = plan.get(0).sub().getLevel();
        TeleportStrands.Carry strands = TeleportStrands.capture(sourceLevel, moving);
        LoconauticsConstants.LOGGER.info("[issue281] {} cars={} strands carried={} cut={} from {} to {}", mode, plan.size(),
                strands.carried(), strands.cut(), sourceLevel.dimension().location(), destLevel.dimension().location());
        Map<UUID, TeleportStrands.Shift> shifts = new HashMap<>();
        Map<UUID, ServerLevel> where = new HashMap<>();

        int moved = 0;
        for (Landing landing : plan) {
            SableTrain train = landing.train();
            GhostWheelBodies.releaseAll(train);
            SableTrainDriver.forgetBodyState(train);
            BlockPos fromCenter = landing.sub().getPlot().getCenterBlock();
            Map<BlockPos, UUID> drivers = drivers(landing.sub());
            Vector3d carriedVelocity = mode == Mode.HANDOVER ? carriedVelocity(landing) : null;
            releaseContacts(landing.sub());

            SableTeleportation.Result result = SableTeleportation.teleport(landing.sub(), destLevel,
                    landing.origin(), landing.orient(), seats.get(landing.sub().getUniqueId()));
            if (!result.ok()) {
                LoconauticsConstants.LOGGER.error("[teleport] train {} stopped: {}", train.id(), result.failure());
                GhostWheelBodies.rebuild(train);
                TeleportStrands.restore(strands, where, sourceLevel, shifts);
                return new Result(result.failure(), moved, cars(plan));
            }
            shifts.put(landing.sub().getUniqueId(), new TeleportStrands.Shift(fromCenter,
                    result.moved().getPlot().getCenterBlock()));
            where.put(landing.sub().getUniqueId(), destLevel);

            train.setLevel(destLevel);
            if (mode == Mode.HANDOVER) {
                train.beginPoseSettle(TrainResidency.POSE_SETTLE_TICKS);
            } else if (mode == Mode.RAIL) {
                double speed = Math.abs(train.speed()) * landing.railTravelSign();
                train.relocate(landing.seat().carriage(), landing.seat().bogeys());
                train.setDerailed(false);
                train.setSpeed(speed);
                train.beginPoseSettle(TrainResidency.POSE_SETTLE_TICKS);
            } else {
                train.setSpeed(0.0);
                train.setTargetSpeed(0.0);
                train.setDerailed(true, "teleport");
            }
            if (carriedVelocity != null) {
                var pipeline = SubLevelContainer.getContainer(destLevel).physicsSystem().getPipeline();
                pipeline.addLinearAndAngularVelocity(result.moved(), carriedVelocity, new Vector3d());
            }
            settleKnuckles(result.moved(), moving);
            reseatDrivers(result.moved(), fromCenter, drivers, trace);
            GhostWheelBodies.rebuild(train);
            SableTrainPersistence.persist(train);
            SableTrainSyncPacket.broadcast(train);
            moved++;
            LoconauticsConstants.LOGGER.info("[issue281] car={} landed {}/{} derailed={} speed={}", shortId(train), moved,
                    plan.size(), train.isDerailed(), train.speed());
            trace.accept("car " + shortId(train) + " landed at " + format(landing.origin()));
        }
        followPartnerPlots(plan, destLevel, shifts);
        holdCoupledContacts(plan, destLevel);
        TeleportStrands.restore(strands, where, sourceLevel, shifts);
        LoconauticsConstants.LOGGER.info("[issue281] all {} cars landed, strands restored", moved);
        if (strands.carried() > 0 || strands.cut() > 0) {
            trace.accept(strands.carried() + " strands respawned, " + strands.cut() + " cut loose");
        }
        return new Result(null, moved, cars(plan));
    }

    // the body is rebuilt on landing and rapier starts it at rest, so the speed is turned with the car and handed back
    private static Vector3d carriedVelocity(Landing landing) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(landing.sub().getLevel());
        if (container == null) {
            return null;
        }
        Vector3d velocity = container.physicsSystem().getPipeline().getLinearVelocity(landing.sub(), new Vector3d());
        Quaterniond turn = new Quaterniond(landing.orient())
                .mul(new Quaterniond(landing.sub().logicalPose().orientation()).invert());
        return turn.transform(velocity);
    }

    // the contact filters are bound to the bodies that are about to be removed, drop them before the body goes
    private static void releaseContacts(ServerSubLevel sub) {
        for (UUID wheel : GhostWheelBodies.wheelBodiesOf(sub.getUniqueId())) {
            SubLevelContacts.releaseAll(wheel);
        }
        SubLevelContacts.releaseAll(sub.getUniqueId());
    }

    // the filters went with the old bodies, so coupled cars would touch for a tick or two after landing
    private static void holdCoupledContacts(List<Landing> plan, ServerLevel destLevel) {
        for (Landing landing : plan) {
            ServerSubLevel moved = SubLevelData.resolve(destLevel, landing.sub().getUniqueId());
            if (moved == null) {
                continue;
            }
            for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(moved, KnuckleBlockEntity.class)) {
                UUID partnerId = knuckle.getPartnerSubLevelId();
                ServerSubLevel partner = partnerId == null ? null : SubLevelData.resolve(destLevel, partnerId);
                if (partner != null) {
                    SubLevelContacts.disableCoupled(moved, partner);
                }
            }
        }
    }

    // each car lands on its own plot, so the partner position a knuckle stored points at the old plot until it is shifted
    private static void followPartnerPlots(List<Landing> plan, ServerLevel destLevel,
                                           Map<UUID, TeleportStrands.Shift> shifts) {
        for (Landing landing : plan) {
            ServerSubLevel moved = SubLevelData.resolve(destLevel, landing.sub().getUniqueId());
            if (moved == null) {
                continue;
            }
            for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(moved, KnuckleBlockEntity.class)) {
                TeleportStrands.Shift partnerShift = shifts.get(knuckle.getPartnerSubLevelId());
                BlockPos matePos = knuckle.getPartnerLocalPos();
                if (partnerShift != null && matePos != null) {
                    knuckle.movePartnerPos(partnerShift.apply(matePos));
                }
            }
        }
    }

    private static void settleKnuckles(ServerSubLevel sub, Set<UUID> moving) {
        for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class)) {
            UUID partner = knuckle.getPartnerSubLevelId();
            if (partner == null || moving.contains(partner)) {
                knuckle.releaseCoupling();
                continue;
            }
            ServerSubLevel partnerSub = SubLevelData.resolve(sub.getLevel(), partner);
            BlockPos matePos = knuckle.getPartnerLocalPos();
            if (partnerSub != null && matePos != null
                    && partnerSub.getLevel().getBlockEntity(matePos) instanceof KnuckleBlockEntity mate) {
                mate.clearConnection();
                mate.clearPartner();
            }
            knuckle.clearConnection();
            knuckle.clearPartner();
            LoconauticsConstants.LOGGER.info("[teleport] uncoupled {} from {}, it is not coming along",
                    sub.getUniqueId(), partner);
        }
    }

    private static Map<BlockPos, UUID> drivers(ServerSubLevel sub) {
        Map<BlockPos, UUID> drivers = new HashMap<>();
        for (AnalogControllerBlockEntity controller : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
            UUID user = controller.getCurrentUser();
            if (user != null) {
                drivers.put(controller.getBlockPos(), user);
            }
        }
        return drivers;
    }

    // the controller reloads from a tag that never carries a driver, a new plot only shifts x and z like repot
    private static void reseatDrivers(ServerSubLevel moved, BlockPos fromCenter, Map<BlockPos, UUID> drivers,
                                      Consumer<String> trace) {
        BlockPos toCenter = moved.getPlot().getCenterBlock();
        ServerLevel level = moved.getLevel();
        if (drivers.isEmpty()) {
            trace.accept("no driver on any controller of " + moved.getUniqueId().toString().substring(0, 8));
        }
        drivers.forEach((pos, user) -> {
            BlockPos landed = new BlockPos(pos.getX() - fromCenter.getX() + toCenter.getX(), pos.getY(),
                    pos.getZ() - fromCenter.getZ() + toCenter.getZ());
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(user);
            boolean found = level.getBlockEntity(landed) instanceof AnalogControllerBlockEntity;
            trace.accept("driver " + (player == null ? user : player.getScoreboardName()) + " at " + pos.toShortString()
                    + " to " + landed.toShortString() + " controller=" + found + " player in "
                    + (player == null ? "none" : player.level().dimension().location()));
            if (player != null && level.getBlockEntity(landed) instanceof AnalogControllerBlockEntity controller) {
                controller.resumeUser(player);
            }
        });
    }

    private static List<SableTrain> cars(List<Landing> plan) {
        List<SableTrain> out = new ArrayList<>();
        for (Landing landing : plan) {
            out.add(landing.train());
        }
        return out;
    }

    private static String format(Vector3d v) {
        return String.format("%.1f %.1f %.1f", v.x, v.y, v.z);
    }

    private static String shortId(SableTrain train) {
        return train.id().toString().substring(0, 8);
    }
}
