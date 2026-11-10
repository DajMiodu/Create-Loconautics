package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Bogey;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.util.SimMathUtils;
import net.minecraft.server.level.ServerLevel;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WeldedBodies {

    private static final double MAX_CARRY_JUMP = 64.0;
    private static final double STRAIGHTEN_SECONDS = 0.4;
    private static final double STRAIGHTEN_SHARE = 0.5;

    private static final class Latch {
        private final Vector3d startPosition;
        private final Quaterniond startOrientation;
        private final Vector3d targetPosition;
        private final Quaterniond targetOrientation;
        private final Vector3d localPosition;
        private final Quaterniond localOrientation;
        private double elapsed;
        private Vector3d lastTarget;
        private Quaterniond lastOrientation;

        Latch(Vector3d position, Quaterniond orientation, Vector3d targetPosition, Quaterniond targetOrientation) {
            this.startPosition = position;
            this.startOrientation = orientation;
            this.targetPosition = targetPosition;
            this.targetOrientation = targetOrientation;
            this.localPosition = new Vector3d(position);
            this.localOrientation = new Quaterniond(orientation);
            this.elapsed = targetPosition == null ? STRAIGHTEN_SECONDS : 0.0;
        }

        boolean settled() {
            return elapsed >= STRAIGHTEN_SECONDS;
        }

        void advance(double dt) {
            if (targetPosition == null || settled()) {
                return;
            }
            elapsed = Math.min(STRAIGHTEN_SECONDS, elapsed + dt);
            double t = elapsed / STRAIGHTEN_SECONDS;
            double ease = t * t * (3.0 - 2.0 * t);
            startPosition.lerp(targetPosition, ease, localPosition);
            startOrientation.slerp(targetOrientation, ease, localOrientation);
        }

        Vector3d localPosition() {
            return localPosition;
        }

        Quaterniond localOrientation() {
            return localOrientation;
        }
    }

    private static final Map<UUID, Map<UUID, Latch>> LATCHES = new HashMap<>();
    private static final Map<UUID, UUID> CLAIMS = new HashMap<>();
    private static final Set<Set<UUID>> SETTLED = new HashSet<>();

    private WeldedBodies() {
    }

    public static void refresh(ServerLevel level, Car carriage) {
        UUID carId = carriage == null ? null : carriage.subLevelId();
        if (carId == null) {
            return;
        }
        ServerSubLevel car = SubLevelData.resolve(level, carId);
        if (car == null) {
            release(carId);
            return;
        }
        Map<UUID, Latch> latches = LATCHES.computeIfAbsent(carId, id -> new HashMap<>());
        Set<UUID> partners = new HashSet<>();
        for (UUID partnerId : TrainConsist.weldedSubLevels(level, carId)) {
            if (SableTrainRegistry.bySubLevel(partnerId) != null && !DockGroups.isFollowerOf(partnerId, carId)) {
                continue;
            }
            ServerSubLevel partner = SubLevelData.resolve(level, partnerId);
            if (partner == null || hostsTheRail(carriage, partnerId)) {
                continue;
            }
            if (partner.logicalPose().position().distanceSquared(car.logicalPose().position())
                    > MAX_CARRY_JUMP * MAX_CARRY_JUMP) {
                continue;
            }
            UUID owner = CLAIMS.get(partnerId);
            if (owner != null && !owner.equals(carId) && LATCHES.containsKey(owner)) {
                continue;
            }
            CLAIMS.put(partnerId, carId);
            partners.add(partnerId);
            if (!latches.containsKey(partnerId)) {
                latches.put(partnerId, latch(car, partner, carriage));
                if (SableTrainRegistry.bySubLevel(partnerId) != null) {
                    SubLevelContacts.disableCoupled(car, partner);
                }
            }
        }
        for (UUID gone : new HashSet<>(latches.keySet())) {
            if (!partners.contains(gone)) {
                SubLevelContacts.enableCoupled(carId, gone);
            }
        }
        latches.keySet().retainAll(partners);
        SETTLED.removeIf(pair -> pair.contains(carId) && !partners.containsAll(minus(pair, carId)));
        if (latches.isEmpty()) {
            LATCHES.remove(carId);
        }
        CLAIMS.entrySet().removeIf(e -> e.getValue().equals(carId) && !partners.contains(e.getKey()));
    }

    private static boolean hostsTheRail(Car car, UUID partnerId) {
        for (Bogey bogey : car.bogeys()) {
            ServerSubLevel host = bogey.rail().activeSub();
            if (host != null && partnerId.equals(host.getUniqueId())) {
                return true;
            }
        }
        ServerSubLevel host = car.carriage() == null ? null : car.carriage().activeSub();
        return host != null && partnerId.equals(host.getUniqueId());
    }

    private static Set<UUID> minus(Set<UUID> pair, UUID id) {
        Set<UUID> rest = new HashSet<>(pair);
        rest.remove(id);
        return rest;
    }

    private static Latch plainLatch(ServerSubLevel car, ServerSubLevel partner, Vector3d local, Quaterniond localQ) {
        SETTLED.add(Set.of(car.getUniqueId(), partner.getUniqueId()));
        return new Latch(local, localQ, null, null);
    }

    private static Latch connectorLatch(ServerSubLevel car, ServerSubLevel partner) {
        for (DockingConnectorBlockEntity own : SubLevelBlocks.findAll(car, DockingConnectorBlockEntity.class)) {
            if (!partner.getUniqueId().equals(own.otherConnectorSubLevelId)) {
                continue;
            }
            DockingConnectorBlockEntity other = own.getOtherConnector();
            if (other == null) {
                continue;
            }
            Pose3d leaderPose = car.logicalPose();
            Pose3d followerPose = partner.logicalPose();
            Quaterniond ownBlock = new Quaterniond(own.getOrientation());
            Quaterniond otherBlock = new Quaterniond(other.getOrientation());
            Quaterniond otherInLeader = new Quaterniond(otherBlock).premul(followerPose.orientation())
                    .premul(new Quaterniond(leaderPose.orientation()).conjugate());
            Quaterniond gridTarget = new Quaterniond();
            SimMathUtils.clampQuaternionToGrid(new Quaterniond(ownBlock).div(otherInLeader),
                    SimMathUtils.GridQuats.REAL.opposite(), gridTarget);
            Quaterniond relative = new Quaterniond(gridTarget).mul(ownBlock).mul(new Quaterniond(otherBlock).conjugate())
                    .normalize();
            Quaterniond followerWorldQ = new Quaterniond(leaderPose.orientation()).mul(relative).normalize();
            Vector3d ownTip = new Vector3d(own.getTipPosition().x, own.getTipPosition().y, own.getTipPosition().z);
            Vector3d otherTip = new Vector3d(other.getTipPosition().x, other.getTipPosition().y, other.getTipPosition().z);
            Vector3d tipWorld = leaderPose.transformPosition(ownTip, new Vector3d());
            Vector3d tipOffset = followerPose.transformPosition(new Vector3d(otherTip), new Vector3d())
                    .sub(followerPose.position());
            Vector3d tipLocal = new Quaterniond(followerPose.orientation()).conjugate().transform(tipOffset, new Vector3d());
            Vector3d followerWorld = tipWorld.sub(followerWorldQ.transform(tipLocal, new Vector3d()));
            Vector3d local = leaderPose.transformPositionInverse(followerWorld, new Vector3d());
            if (!local.isFinite() || !relative.isFinite()) {
                continue;
            }
            return new Latch(local, relative, null, null);
        }
        return null;
    }

    private static Latch latch(ServerSubLevel car, ServerSubLevel partner, Car carriage) {
        Vector3d local = car.logicalPose().transformPositionInverse(
                partner.logicalPose().position(), new Vector3d());
        Quaterniond localQ = new Quaterniond(car.logicalPose().orientation()).conjugate()
                .mul(partner.logicalPose().orientation());
        SableTrain follower = SableTrainRegistry.bySubLevel(partner.getUniqueId());
        Car followerCar = follower == null ? null : follower.car();
        Latch docked = connectorLatch(car, partner);
        if (docked != null) {
            SETTLED.add(Set.of(car.getUniqueId(), partner.getUniqueId()));
            return docked;
        }
        if (carriage == null || followerCar == null || carriage.localLead() == null || carriage.localTrail() == null
                || followerCar.localLead() == null || followerCar.localTrail() == null) {
            return plainLatch(car, partner, local, localQ);
        }
        Pose3d leaderPose = car.logicalPose();
        Pose3d followerPose = partner.logicalPose();
        Vector3d followerMid = new Vector3d(followerCar.localLead()).add(followerCar.localTrail()).mul(0.5);
        Vector3d axisLeader = leaderPose.orientation().transform(
                new Vector3d(carriage.localLead()).sub(carriage.localTrail()).normalize(), new Vector3d());
        Vector3d axisFollower = followerPose.orientation().transform(
                new Vector3d(followerCar.localLead()).sub(followerCar.localTrail()).normalize(), new Vector3d());
        if (!axisLeader.isFinite() || !axisFollower.isFinite()) {
            return plainLatch(car, partner, local, localQ);
        }
        if (axisFollower.dot(axisLeader) < 0.0) {
            axisLeader.negate();
        }
        Vector3d followerMidWorld = followerPose.transformPosition(followerMid, new Vector3d());
        Vector3d midOffset = new Vector3d(followerMidWorld).sub(followerPose.position());
        Vector3d midLocal = new Quaterniond(followerPose.orientation()).conjugate().transform(midOffset, new Vector3d());
        Quaterniond turn = new Quaterniond().rotationTo(axisFollower, axisLeader);
        turn = new Quaterniond().slerp(turn, STRAIGHTEN_SHARE);
        Quaterniond targetWorldQ = new Quaterniond(turn).mul(followerPose.orientation()).normalize();
        Vector3d targetWorld = new Vector3d(followerMidWorld).sub(targetWorldQ.transform(midLocal, new Vector3d()));
        Vector3d targetLocal = leaderPose.transformPositionInverse(targetWorld, new Vector3d());
        Quaterniond targetLocalQ = new Quaterniond(leaderPose.orientation()).conjugate().mul(targetWorldQ).normalize();
        if (!targetLocal.isFinite() || !targetLocalQ.isFinite()) {
            return plainLatch(car, partner, local, localQ);
        }
        return new Latch(local, localQ, targetLocal, targetLocalQ);
    }

    public static boolean pairSettled(UUID a, UUID b) {
        return SETTLED.contains(Set.of(a, b));
    }

    public static void carry(PhysicsPipeline pipeline, ServerLevel level, ServerSubLevel car, double dt) {
        Map<UUID, Latch> latches = LATCHES.get(car.getUniqueId());
        if (latches == null || latches.isEmpty()) {
            return;
        }
        Vector3d carVelocity = new Vector3d();
        Vector3d carSpin = new Vector3d();
        RigidBodyHandle carBody = RigidBodyHandle.of(car);
        if (carBody != null) {
            carBody.getLinearVelocity(carVelocity);
            carBody.getAngularVelocity(carSpin);
        }
        for (Map.Entry<UUID, Latch> entry : Map.copyOf(latches).entrySet()) {
            ServerSubLevel partner = SubLevelData.resolve(level, entry.getKey());
            if (partner == null) {
                drop(car.getUniqueId(), entry.getKey());
                continue;
            }
            Latch latch = entry.getValue();
            latch.advance(dt);
            if (latch.settled()) {
                SETTLED.add(Set.of(car.getUniqueId(), entry.getKey()));
            }
            Vector3d target = car.logicalPose().transformPosition(
                    new Vector3d(latch.localPosition()), new Vector3d());
            if (target.distanceSquared(partner.logicalPose().position()) > MAX_CARRY_JUMP * MAX_CARRY_JUMP) {
                drop(car.getUniqueId(), entry.getKey());
                continue;
            }
            Quaterniond orientation = new Quaterniond(car.logicalPose().orientation())
                    .mul(latch.localOrientation());
            if (!target.isFinite() || !orientation.isFinite()) {
                LoconauticsConstants.LOGGER.warn("[dock] carry of {} by {} skipped, target {} orientation {}",
                        entry.getKey(), car.getUniqueId(), target, orientation);
                continue;
            }
            Pose3d pose = partner.logicalPose();
            pose.position().set(target);
            pose.orientation().set(orientation);
            pipeline.teleport(partner, target, orientation);
            pipeline.resetVelocity(partner);
            Vector3d lever = new Vector3d(target).sub(car.logicalPose().position());
            SableTrain partnerTrain = SableTrainRegistry.bySubLevel(entry.getKey());
            Vector3d linear = new Vector3d(carSpin).cross(lever).add(carVelocity);
            Vector3d spin = carSpin;
            if (partnerTrain != null && latch.lastTarget != null && dt > 1.0e-6) {
                linear = new Vector3d(target).sub(latch.lastTarget).div(dt);
                spin = angularVelocity(latch.lastOrientation, orientation, dt);
            }
            latch.lastTarget = new Vector3d(target);
            latch.lastOrientation = new Quaterniond(orientation);
            pipeline.addLinearAndAngularVelocity(partner, linear, spin);
            if (partnerTrain != null) {
                GhostWheelBodies.holdPivots(partner, partnerTrain);
            }
        }
    }

    public static Vector3d angularVelocity(Quaterniond from, Quaterniond to, double dt) {
        Quaterniond delta = new Quaterniond(to).mul(new Quaterniond(from).conjugate()).normalize();
        AxisAngle4d turn = new AxisAngle4d(delta);
        double angle = turn.angle > Math.PI ? turn.angle - 2.0 * Math.PI : turn.angle;
        Vector3d spin = new Vector3d(turn.x, turn.y, turn.z).mul(angle / dt);
        return spin.isFinite() ? spin : new Vector3d();
    }

    private static void drop(UUID carId, UUID partnerId) {
        Map<UUID, Latch> latches = LATCHES.get(carId);
        if (latches != null) {
            latches.remove(partnerId);
            if (latches.isEmpty()) {
                LATCHES.remove(carId);
            }
        }
        CLAIMS.remove(partnerId);
        SETTLED.remove(Set.of(carId, partnerId));
        SubLevelContacts.enableCoupled(carId, partnerId);
    }

    public static void release(UUID carId) {
        SETTLED.removeIf(pair -> pair.contains(carId));
        Map<UUID, Latch> latches = LATCHES.remove(carId);
        if (latches != null) {
            latches.keySet().forEach(partnerId -> SubLevelContacts.enableCoupled(carId, partnerId));
            CLAIMS.keySet().removeAll(latches.keySet());
        }
    }

    public static void retain(Set<UUID> liveCars) {
        LATCHES.keySet().retainAll(liveCars);
        CLAIMS.values().retainAll(liveCars);
        CLAIMS.entrySet().removeIf(e -> !LATCHES.getOrDefault(e.getValue(), Map.of()).containsKey(e.getKey()));
    }
}
