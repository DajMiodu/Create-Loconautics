package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.foundation.debug.SteerTrace;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.UUID;

final class TrainSteering {

    static void tickSteering(SableTrain train) {
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

        BearingAxleBlockEntity steerAxle = TrainPropulsion.bearingAxleIn(sub);
        int input = steerAxle == null ? 0 : steerAxle.getSteerInput();
        Direction front = axleFront(steerAxle, sub);

        for (SableTrain.Bogey bogey : car.bogeys()) {
            SteerTrace.owner(bogey.rail(), car.subLevelId());
        }
        tagJunctions(train, car.subLevelId() + " mode=" + describeMode(train, input, front));

        if (front == null || input == 0) {
            SableTrain ahead = leaderAhead(train);
            SableTrain leader = drivenLeader(train);
            boolean isLeader = leader != null && car.subLevelId().equals(leader.car().subLevelId());
            if (ahead != null && followAhead(train, ahead)) {
                SteerTrace.mode(car.subLevelId(), (isLeader ? "driver" : "car") + " follows " + ahead.car().subLevelId().toString().substring(0, 8));
                if (isLeader) {
                    train.setSteerIntent(null);
                }
                applySteerTarget(car, isLeader ? null : leaderIntent(train));
                return;
            }
            if (ahead == null && leader != null && !car.subLevelId().equals(leader.car().subLevelId())) {
                SteerTrace.mode(car.subLevelId(), "front car takes driver intent " + leader.steerIntent());
                clearFollow(car);
                applySteerTarget(car, leader.steerIntent());
                return;
            }
        }

        SteerTrace.mode(car.subLevelId(), "own input " + input + " ahead=" + (front == null || input == 0 ? leaderAhead(train) != null : "n/a"));
        train.setSteerIntent(target(front, input));
        clearFollow(car);
        applySteerTarget(car, train.steerIntent());
    }

    private static final java.util.Map<UUID, String> ISSUE274_MODE = new java.util.HashMap<>();

    private static String describeMode(SableTrain train, int input, Direction front) {
        SableTrain leader = drivenLeader(train);
        SableTrain ahead = leaderAhead(train);
        boolean isLeader = leader != null && train.car().subLevelId().equals(leader.car().subLevelId());
        return (front == null || input == 0 ? "noinput" : "input" + input) + (isLeader ? " leader" : " other")
                + (ahead == null ? " ahead=none" : " ahead=" + ahead.car().subLevelId().toString().substring(0, 8))
                + (leader == null ? " driver=none" : " driver=" + leader.car().subLevelId().toString().substring(0, 8)
                        + " driverIntent=" + leader.steerIntent())
                + " ownPath=" + (leader != null && isLeader && leader.leadingBogeyRail() != null
                        && leader.leadingBogeyRail().remainingPath() != null);
    }

    private static void tagJunctions(SableTrain train, String mode) {
        Car car = train.car();
        String tag = car.subLevelId().toString().substring(0, 8) + " " + mode.substring(mode.indexOf("mode="));
        if (!tag.equals(ISSUE274_MODE.put(car.subLevelId(), tag))) {
            com.lycoris.loconautics.LoconauticsConstants.LOGGER.info("[issue274] car {}", tag);
        }
        for (SableTrain.Bogey bogey : car.bogeys()) {
            bogey.rail().setJunctionTag(tag);
        }
        if (car.carriage() != null) {
            car.carriage().setJunctionTag(tag);
        }
    }

    static void clearFollow(Car car) {
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (bogey.rail() != null) {
                bogey.rail().clearFollowPoint();
            }
        }
    }

    static Direction target(Direction front, int input) {
        return front == null || input == 0 ? null
                : input < 0 ? front.getCounterClockWise() : front.getClockWise();
    }

    static Direction leaderIntent(SableTrain train) {
        SableTrain leader = drivenLeader(train);
        return leader == null ? null : leader.steerIntent();
    }

    static void applySteerTarget(Car car, Direction target) {
        Vec3 goal = target == null ? null : new Vec3(target.getStepX(), 0.0, target.getStepZ());
        for (SableTrain.Bogey bogey : car.bogeys()) {
            bogey.rail().setSteerTarget(goal);
        }
        if (car.carriage() != null) {
            car.carriage().setSteerTarget(goal);
        }
    }

    static Direction axleFront(BearingAxleBlockEntity axle, ServerSubLevel sub) {
        if (axle == null) {
            return null;
        }
        BlockState state = axle.getBlockState();
        if (!(state.getBlock() instanceof BearingAxleBlock)) {
            return null;
        }
        Direction local = BearingAxleBlock.front(state);
        Vector3d dir = new Vector3d(local.getStepX(), 0.0, local.getStepZ());
        sub.logicalPose().transformNormal(dir, dir);
        if (dir.x * dir.x + dir.z * dir.z < 1.0e-9) {
            return null;
        }
        return Direction.getNearest(dir.x, 0.0, dir.z);
    }

    static boolean followAhead(SableTrain train, SableTrain ahead) {
        RailCarriage chaser = null;
        TravellingPoint target = null;
        double best = Double.MAX_VALUE;
        if (train.isTrackless() || ahead.isTrackless()) {
            SteerTrace.mode(train.car().subLevelId(), "follow refused, trackless " + train.isTrackless() + " ahead " + ahead.isTrackless());
            return false;
        }
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            RailCarriage mine = bogey.rail();
            Vec3 minePos = mine.leadingPos();
            if (minePos == null) {
                continue;
            }
            for (SableTrain.Bogey other : ahead.car().bogeys()) {
                RailCarriage rail = other.rail();
                if (rail.graph() != mine.graph()) {
                    continue;
                }
                Vec3 leadingPos = rail.leadingPos();
                if (leadingPos != null && leadingPos.distanceToSqr(minePos) < best) {
                    best = leadingPos.distanceToSqr(minePos);
                    chaser = mine;
                    target = rail.leading();
                }
                Vec3 trailingPos = rail.trailingPos();
                if (trailingPos != null && trailingPos.distanceToSqr(minePos) < best) {
                    best = trailingPos.distanceToSqr(minePos);
                    chaser = mine;
                    target = rail.trailing();
                }
            }
        }
        if (chaser == null || target == null) {
            SteerTrace.mode(train.car().subLevelId(), "follow refused, no point on the same graph");
            return false;
        }
        chaser.followPoint(target);
        return true;
    }

    static SableTrain drivenLeader(SableTrain train) {
        ServerLevel level = train.level();
        UUID myId = train.car().subLevelId();
        if (level == null || myId == null) {
            return null;
        }
        java.util.Set<UUID> consist = TrainConsist.connectedSubLevels(level, myId);
        if (consist.size() <= 1) {
            return null;
        }
        return ConsistTurns.governing(train);
    }

    static SableTrain leaderAhead(SableTrain train) {
        SableTrain leader = drivenLeader(train);
        UUID myId = train.car().subLevelId();
        if (leader == null || myId == null) {
            return null;
        }
        UUID leaderId = leader.car().subLevelId();
        java.util.List<UUID> chain = TrainConsist.orderedChain(train.level(), leaderId);
        int idx = chain.indexOf(myId);
        int leaderIdx = chain.indexOf(leaderId);
        if (idx < 0 || leaderIdx < 0) {
            return null;
        }
        // every car uses the drivers direction, a cars own speed sign flickers and made neighbors follow each other
        int dir = frontDirection(leader, chain, leaderIdx);
        if (dir != 0) {
            return neighbor(chain, idx + dir);
        }
        // orderedchain can come back ordered from the far end, so at a stand step toward the loco, never guess
        if (idx == leaderIdx) {
            return null;
        }
        int toward = leaderIdx < idx ? -1 : 1;
        SableTrain near = neighbor(chain, idx + toward);
        return near != null ? near : neighbor(chain, idx - toward);
    }

    private static SableTrain neighbor(java.util.List<UUID> chain, int index) {
        return index >= 0 && index < chain.size() ? SableTrainRegistry.bySubLevel(chain.get(index)) : null;
    }

    private static int frontDirection(SableTrain leader, java.util.List<UUID> chain, int leaderIdx) {
        SableTrain before = neighbor(chain, leaderIdx - 1);
        SableTrain after = neighbor(chain, leaderIdx + 1);
        if (before == null && after == null) {
            return 0;
        }
        Vec3 travel = travelDirection(leader);
        if (travel == null) {
            return 0;
        }
        if (leader.isTrackless()) {
            return before != null ? -1 : 1;
        }
        Vec3 mine = leader.car().carriage().leadingPos();
        if (mine == null) {
            return before != null ? -1 : 1;
        }
        int best = 0;
        double bestDot = 0.0;
        SableTrain[] candidates = {before, after};
        for (int i = 0; i < candidates.length; i++) {
            SableTrain candidate = candidates[i];
            if (candidate == null || candidate.isTrackless()) {
                continue;
            }
            Vec3 pos = candidate.car().carriage().leadingPos();
            if (pos != null && pos.subtract(mine).dot(travel) > bestDot) {
                bestDot = pos.subtract(mine).dot(travel);
                best = i == 0 ? -1 : 1;
            }
        }
        return best;
    }

    static Vec3 travelDirection(SableTrain train) {
        RailCarriage rail = train.leadingBogeyRail();
        if (rail == null) {
            rail = train.car().carriage();
        }
        Vec3 forward = rail == null ? null : rail.forward();
        if (forward == null || Math.abs(train.speed()) < 1.0e-4) {
            return null;
        }
        return forward.scale(Math.signum(train.speed()));
    }

    static boolean isDrivenLeader(SableTrain t) {
        if (t.isDerailed() || t.isPhysics()) {
            return false;
        }
        UUID id = t.car().subLevelId();
        ServerSubLevel sub = SubLevelData.resolve(t.level(), id);
        if (sub == null) {
            return false;
        }
        AnalogControllerBlockEntity c = SableTrainDriver.analogControllerIn(sub);
        return c != null && (c.hasUser() || c.isScheduleControlled());
    }

    private TrainSteering() {
    }
}
