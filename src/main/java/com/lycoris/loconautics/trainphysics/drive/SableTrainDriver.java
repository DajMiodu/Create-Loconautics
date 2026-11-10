package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.common.impact.TrainKnockback;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.ScheduleReadiness;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.glue.RailGlue;
import com.lycoris.loconautics.trainphysics.glue.RailSnap;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.spawn.WheelColliders;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.state.StationChunkTickets;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.*;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class SableTrainDriver {

    private SableTrainDriver() {
    }

    public static void register() {
        SableEventPlatform.INSTANCE.onPhysicsTick(SableTrainDriver::onPhysicsTick);
        SableEventPlatform.INSTANCE.onPostPhysicsTick(SableTrainDriver::onPostPhysicsTick);
        com.lycoris.loconautics.trainphysics.portal.PortalTwins.register();
    }

    static final double MAX_BOGEY_VISUAL_OFFSET = 1.5;
    static final double DRIVE_K = 6.0;
    static final double MAX_SUBSTEP_SLIDE = 2.0;

    static final java.util.Set<UUID> DEAD_END = new java.util.HashSet<>();
    static final java.util.Map<UUID, Integer> HOP_SCAN = new java.util.HashMap<>();
    static final int HOP_SCAN_STEPS = 5;
    // a scan onto a sub level rail create has not meshed yet misses once and hits on retry, and the derail check reads deadend in between, so one miss must not condemn the car
    static final java.util.Map<UUID, Integer> DEAD_END_MISS = new java.util.HashMap<>();
    static final int DEAD_END_MISSES = 2;

    public static void onPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        forEachCar(system, (pipeline, container, sub, train, car) -> {
            if (train.isDerailed() || train.isPhysics()) {
                RailGlue.releaseConstraint(sub.getUniqueId());
                clearCrossState(sub.getUniqueId());
                WeldedBodies.release(sub.getUniqueId());
                if (train.isDerailed()) {
                    ExternalForceDerail.sample(sub, car, train, timeStep);
                    ExternalForceDerail.dampRelease(sub, train);
                    if (!com.lycoris.loconautics.trainphysics.spawn.RerailApproach.holdApproach(pipeline, sub, train)) {
                        applyDerailedGroundDrive(sub, car, train, timeStep);
                        TrainKnockback.applyPendingKnockback(pipeline, sub, train);
                    }
                }
                return;
            }
            if (DockGroups.isFollower(sub.getUniqueId())) {
                RailGlue.releaseConstraint(sub.getUniqueId());
                com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.retarget(pipeline, sub, train);
                return;
            }
            if (beingPulled(sub)) {
                RailGlue.releaseConstraint(sub.getUniqueId());
                return;
            }
            DockApproach.drive(sub, train);
            Car group = DockGroups.composite(sub, train, car);
            capTrainSpeed(sub, group, train);
            com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.retarget(pipeline, sub, train);
            if (useForceGlue(train, group)) {
                ExternalForceDerail.sample(sub, group, train, timeStep);
                driveConstraintGlued(pipeline, sub, group, timeStep, train);
            } else {
                advancePinned(sub, group, train, timeStep);
                pinCar(pipeline, sub, group, false, train);
            }
        });
        com.lycoris.loconautics.content.knuckle.KnuckleRailCoupler.solve(system, timeStep);
    }

    public static void onPostPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        forEachCar(system, (pipeline, container, sub, train, car) -> {
            if (train.isDerailed() || train.isPhysics()) {
                WeldedBodies.release(sub.getUniqueId());
                return;
            }
            if (train.hasOffTrackBogey() || DockGroups.isFollower(sub.getUniqueId()) || beingPulled(sub)) {
                return;
            }
            Car group = DockGroups.composite(sub, train, car);
            if (useForceGlue(train, group)) {
                RailSnap.snapToRail(pipeline, sub, group, train, timeStep);
            } else {
                pinCar(pipeline, sub, group, true, train);
            }
            WeldedBodies.carry(pipeline, train.level(), sub, timeStep);
        });
    }

    private static boolean beingPulled(ServerSubLevel sub) {
        return sub.getLevel() instanceof ServerLevel level
                && SubLevelMergeWatcher.pulling(level, sub.getUniqueId());
    }

    static final double MAX_FOLLOW_CORRECTION = 0.5;

    static final double TRAVEL_SIGN_FLIP_DEADZONE = 0.09;

    static void pinCar(PhysicsPipeline pipeline, ServerSubLevel bodySub, Car car, boolean post, SableTrain train) {
        RailSnap.RailSeat seat = RailSnap.railSeat(bodySub, car);
        if (seat != null) {
            RailSnap.pin(pipeline, bodySub, seat, post, car, train);
        }
    }

    public static Quaterniond bodyPose(Car car, Vector3d centerDst) {
        if (car.carriage() == null) {
            return null;
        }
        var bogeys = car.chain();
        if (bogeys.size() >= 2) {
            Vector3d first = bogeys.get(0).rail().center();
            Vector3d last = bogeys.get(bogeys.size() - 1).rail().center();
            if (first != null && last != null) {
                Vector3d forward = new Vector3d(last).sub(first);
                centerDst.set(first).add(last).mul(0.5);
                return car.carriage().orientationTo(forward);
            }
        }
        Vector3d c = car.carriage().center();
        if (c == null) {
            return null;
        }
        centerDst.set(c);
        return car.carriage().orientation();
    }

    public static void seedRailVelocity(ServerSubLevel sub, Car car, SableTrain train) {
        if (!useForceGlue(car) || train.speed() == 0.0) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        Vector3d[] ref = TrainRailAdvance.bogeyReference(sub, car);
        Vector3d tangent = ref != null ? ref[1] : forwardUnit(car.carriage());
        if (handle == null || tangent == null) {
            return;
        }
        Vector3d velocity = railVelocityUnder(sub, car).add(new Vector3d(tangent).mul(train.speed() * 20.0));
        handle.addLinearAndAngularVelocity(velocity, new Vector3d());
    }

    static void advancePinned(ServerSubLevel sub, Car car, SableTrain train, double dt) {
        double slide = train.speed() * 20.0 * dt;
        if (slide == 0.0) {
            return;
        }
        slide = Math.max(-MAX_SUBSTEP_SLIDE, Math.min(MAX_SUBSTEP_SLIDE, slide));
        TrainRailAdvance.advanceRail(car, slide, TrainRailAdvance.loneFacing(sub, car));
        TrainRailAdvance.animateBogeyWheels(car, sub, slide);
    }

    public static boolean useForceGlue(Car car) {
        return car.localLead() != null && car.localTrail() != null;
    }

    public static boolean useForceGlue(SableTrain train, Car car) {
        return !train.isPoseSettling() && useForceGlue(car);
    }

    static void updateDeadEnd(ServerSubLevel bodySub, Car car, SableTrain train) {
        UUID bodyId = bodySub.getUniqueId();
        boolean atPortal = TrainRailAdvance.anyPendingPortal(car);
        boolean ended = !atPortal && (train.hasOffTrackBogey()
                ? TrainRailAdvance.anyRailedReachedEnd(car, train) : TrainRailAdvance.anyReachedEnd(car));
        if (!ended) {
            DEAD_END.remove(bodyId);
            DEAD_END_MISS.remove(bodyId);
            HOP_SCAN.remove(bodyId);
        } else if (DEAD_END_MISS.getOrDefault(bodyId, 0) < DEAD_END_MISSES
                || HOP_SCAN.merge(bodyId, -1, Integer::sum) <= 0) {
            HOP_SCAN.put(bodyId, HOP_SCAN_STEPS);
            if (TrainRailAdvance.anyEndedFar(car, train)) {
                if (DEAD_END_MISS.merge(bodyId, 1, Integer::sum) >= DEAD_END_MISSES) {
                    DEAD_END.add(bodyId);
                }
            } else if (TrainRailAdvance.weldEndedFollowers(bodySub, car, train)) {
                DEAD_END.remove(bodyId);
                DEAD_END_MISS.remove(bodyId);
            } else if (TrainRailAdvance.ownGraphRailAhead(car, bodySub, train)) {
                DEAD_END.remove(bodyId);
                DEAD_END_MISS.remove(bodyId);
            } else if (DEAD_END_MISS.merge(bodyId, 1, Integer::sum) >= DEAD_END_MISSES) {
                DEAD_END.add(bodyId);
            }
        }
    }

    static void driveConstraintGlued(PhysicsPipeline pipeline, ServerSubLevel bodySub,
                                             Car car, double dt, SableTrain train) {
        if (train.hasOffTrackBogey()) {
            driveConstraintPivot(pipeline, bodySub, car, dt, train);
            return;
        }
        RailCarriage carriage = car.carriage();
        Vector3d[] ref = TrainRailAdvance.bogeyReference(bodySub, car);
        Vector3d railPoint;
        Vector3d tangent;
        if (ref != null) {
            railPoint = ref[0];
            tangent = ref[1];
        } else {
            railPoint = carriage.center();
            if (railPoint == null) {
                return;
            }
            tangent = forwardUnit(carriage);
        }
        Vector3dc localAnchor = RailSnap.glueAnchorLocal(bodySub, car);

        ServerSubLevel slideTrackSub = TrainRailAdvance.leadingActiveSub(car);
        readBackSpeed(train, bodySub, slideTrackSub, railPoint, tangent, dt);
        double slide = train.speed() * 20.0 * dt;
        slide = Math.max(-MAX_SUBSTEP_SLIDE, Math.min(MAX_SUBSTEP_SLIDE, slide));
        TrainRailAdvance.advanceRail(car, slide, TrainRailAdvance.loneFacing(bodySub, car));
        TrainRailAdvance.animateBogeyWheels(car, bodySub, slide);
        RailSnap.noteSlide(bodySub.getUniqueId(), slide);

        updateDeadEnd(bodySub, car, train);

        Quaterniond q;
        ref = TrainRailAdvance.bogeyReference(bodySub, car);
        if (ref != null) {
            railPoint = ref[0];
            tangent = ref[1];
            q = carriage.orientationTo(ref[1]);
        } else {
            railPoint = carriage.center();
            if (railPoint == null) {
                return;
            }
            tangent = forwardUnit(carriage);
            q = carriage.orientation();
        }
        q = RailGlue.limitGlueRotation(bodySub.getUniqueId(), q);

        ServerSubLevel trackSub = TrainRailAdvance.leadingActiveSub(car);
        if (!RailGlue.glueBody(pipeline, bodySub, localAnchor, railPoint, tangent, q,
                RailGlue.GlueMode.RIGID, trackSub)) {
            train.setDerailed(true);
            return;
        }

        if (train.isPowered() || train.servoBrake()) {
            applyTangentialDrive(pipeline, bodySub, trackSub, railPoint, tangent, dt, train);
        }

        double resistance = train.isBraked() ? train.brakeScale() : TrainBrakes.rollingScale(train, bodySub);
        TrainBrakes.applyResistance(bodySub, trackSub, railPoint, tangent, dt, resistance);
        TrainBrakes.relieveSlopeGravity(bodySub, tangent, dt);
    }

    static final double SPEED_GAIN_LIMIT = 12.0;



    static void readBackSpeed(SableTrain train, ServerSubLevel bodySub, ServerSubLevel trackSub,
                              Vector3d railPoint, Vector3d tangent, double dt) {
        RigidBodyHandle handle = RigidBodyHandle.of(bodySub);
        if (handle == null) {
            return;
        }
        Vector3d vel = handle.getLinearVelocity(new Vector3d());
        double measured = TrackHostMotion.relativeAlongTrack(
                bodySub.getLevel(), trackSub, railPoint, vel, tangent) / 20.0;
        double previous = train.speed();
        double from = measured * previous < 0.0 ? 0.0 : previous;
        double away = Math.abs(measured) - Math.abs(from);
        double gain = SPEED_GAIN_LIMIT * dt / 20.0
                + com.lycoris.loconautics.content.knuckle.KnuckleRailCoupler.allowance(bodySub.getUniqueId());
        if (away <= gain) {
            train.setSpeed(measured);
            return;
        }
        double allowed = Math.signum(measured) * (Math.abs(from) + gain);
        train.setSpeed(allowed);
        MassData md = bodySub.getMassTracker();
        double mass = (md != null && !md.isInvalid()) ? md.getMass() : 1.0;
        Vector3d worldImpulse = new Vector3d(tangent).mul(mass * (allowed - measured) * 20.0);
        Vector3d localImpulse = new Quaterniond(bodySub.logicalPose().orientation())
                .conjugate().transform(worldImpulse, new Vector3d());
        ForceTotal forces = new ForceTotal();
        forces.applyLinearImpulse(localImpulse);
        handle.applyForcesAndReset(forces);
    }

    static Vec3 bodyUpWorld(ServerSubLevel sub) {
        Vector3d u = sub.logicalPose().transformNormal(new Vector3d(0.0, 1.0, 0.0), new Vector3d());
        return new Vec3(u.x, u.y, u.z);
    }

    static final double DEFAULT_WHEEL_DROP = 1.5;

    static void driveConstraintPivot(PhysicsPipeline pipeline, ServerSubLevel bodySub, Car car, double dt,
                                             SableTrain train) {
        var bogeys = car.bogeys();
        int railedIdx = -1;
        for (int i = 0; i < bogeys.size(); i++) {
            if (!train.isBogeyOffTrack(bogeys.get(i).localPos())) {
                railedIdx = i;
                break;
            }
        }
        if (railedIdx < 0) {
            return;
        }
        SableTrain.Bogey railed = bogeys.get(railedIdx);
        Vector3dc localAnchor = pivotLocalAnchor(bodySub, car, railed);

        Vector3d railPoint = railed.rail().center();
        Vector3d tangent = TrainRailAdvance.railTangentOf(railed);
        if (railPoint == null || tangent == null) {
            return;
        }

        Vector3d anchorWorld = bodySub.logicalPose().transformPosition(new Vector3d(localAnchor), new Vector3d());
        double slide = anchorWorld.sub(railPoint, new Vector3d()).dot(tangent);
        slide = Math.max(-MAX_SUBSTEP_SLIDE, Math.min(MAX_SUBSTEP_SLIDE, slide));
        railed.rail().unstick();
        railed.rail().setSpeed(slide);
        railed.rail().tick();
        updateDeadEnd(bodySub, car, train);

        railPoint = railed.rail().center();
        tangent = TrainRailAdvance.railTangentOf(railed);
        if (railPoint == null || tangent == null) {
            return;
        }

        ServerSubLevel pivotTrackSub = railed.rail().activeSub();
        if (!RailGlue.glueBody(pipeline, bodySub, localAnchor, railPoint, tangent,
                new Quaterniond(bodySub.logicalPose().orientation()), RailGlue.GlueMode.PIVOT,
                pivotTrackSub)) {
            train.setDerailed(true);
            return;
        }

        if (train.isPowered() || train.servoBrake()) {
            applyTangentialDrive(pipeline, bodySub, pivotTrackSub, railPoint, tangent, dt, train);
        }
        TrainBrakes.applyResistance(bodySub, pivotTrackSub, railPoint, tangent, dt,
                train.isBraked() ? train.brakeScale() : TrainBrakes.rollingScale(train, bodySub));
        TrainBrakes.relieveSlopeGravity(bodySub, tangent, dt);

        readBackSpeed(train, bodySub, pivotTrackSub, railPoint, tangent, dt);
    }

    public static Vector3dc pivotLocalAnchor(ServerSubLevel sub, Car car, SableTrain.Bogey railed) {
        BlockPos cell = sub.getPlot().getCenterBlock().offset(railed.localPos());
        Vector3d anchor = new Vector3d(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
        Vector3dc lead = car.localLead();
        Vector3dc trail = car.localTrail();
        if (lead != null && trail != null) {
            anchor.y = (lead.y() + trail.y()) * 0.5;
        }
        return anchor;
    }

    static void applyTangentialDrive(PhysicsPipeline pipeline, ServerSubLevel sub, ServerSubLevel trackSub,
                                     Vector3d railPoint, Vector3d tangent, double dt, SableTrain train) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        MassData md = sub.getMassTracker();
        double carMass = (md != null && !md.isInvalid()) ? md.getMass() : 1.0;

        Vector3d vel = handle.getLinearVelocity(new Vector3d());
        Vector3d relVel = new Vector3d(vel).sub(
                TrackHostMotion.railVelocity(sub.getLevel(), trackSub, railPoint, new Vector3d()));
        double vTarget = train.targetSpeed() * 20.0;
        double vCurrent = relVel.dot(tangent);
        double desiredAccel = DRIVE_K * (vTarget - vCurrent);

        boolean braking = TrainPropulsion.isBraking(vTarget, vCurrent);
        Vector3d offRail = new Vector3d(relVel).sub(new Vector3d(tangent).mul(relVel.dot(tangent)));
        boolean falling = offRail.y < -1.0e-3 && !train.hasOffTrackBogey();
        if (!braking && falling) {
            return;
        }
        double limit = braking ? TrainPropulsion.brakeLimit(train, vTarget, vCurrent)
                : TrainPropulsion.tractionAccelLimit(train, ConsistPower.tractionMass(train, carMass));
        double accel = Math.max(-limit, Math.min(limit, desiredAccel));

        Vector3d worldImpulse = new Vector3d(tangent).mul(carMass * accel * dt);
        Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation())
                .conjugate().transform(worldImpulse, new Vector3d());
        pipeline.wakeUp(sub);
        ForceTotal forces = new ForceTotal();
        forces.applyLinearImpulse(localImpulse);
        handle.applyForcesAndReset(forces);
        com.lycoris.loconautics.content.knuckle.KnuckleRailCoupler.noteExternal(sub.getUniqueId(), accel * dt);
        TrackHostMotion.pushHost(trackSub, railPoint, deliveredImpulse(sub, worldImpulse, tangent, carMass, vCurrent));
    }

    static final java.util.Map<UUID, Double> LAST_TRACTION_V = new java.util.HashMap<>();

    private static Vector3dc deliveredImpulse(ServerSubLevel sub, Vector3d commanded, Vector3d tangent,
                                              double locoMass, double vCurrent) {
        Double last = LAST_TRACTION_V.put(sub.getUniqueId(), vCurrent);
        if (last == null) {
            return commanded;
        }
        double commandedAlong = commanded.dot(tangent);
        double deliveredAlong = locoMass * (vCurrent - last);
        double along = commandedAlong >= 0.0
                ? Math.max(0.0, Math.min(deliveredAlong, commandedAlong))
                : Math.min(0.0, Math.max(deliveredAlong, commandedAlong));
        return new Vector3d(tangent).mul(along);
    }

    static void applyDerailedGroundDrive(ServerSubLevel sub, Car car, SableTrain train, double dt) {
        double slip = LoconauticsConfig.DERAILED_SLIP_FACTOR.get();
        double wheelDrive = train.wheelDrive();
        if (slip <= 0.0 || Math.abs(wheelDrive) < 1.0e-6) {
            return;
        }
        if (!anyBogeyGrounded(train.level(), sub, car)) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        Vector3d forward = TrainRailAdvance.bodyForwardWorld(sub, car);
        forward.y = 0.0;
        if (forward.lengthSquared() < 1.0e-9) {
            return;
        }
        forward.normalize();

        double vTarget = wheelDrive * slip * 20.0;
        Vector3d vel = handle.getLinearVelocity(new Vector3d());
        double vCurrent = vel.dot(forward);

        MassData md = sub.getMassTracker();
        double locoMass = (md != null && !md.isInvalid()) ? md.getMass() : 1.0;
        double desiredAccel = DRIVE_K * (vTarget - vCurrent);
        double limit = TrainPropulsion.tractionAccelLimit(train, ConsistPower.consistMass(train, locoMass));
        double accel = Math.max(-limit, Math.min(limit, desiredAccel));

        Vector3d worldImpulse = new Vector3d(forward).mul(locoMass * accel * dt);
        Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation())
                .conjugate().transform(worldImpulse, new Vector3d());
        ForceTotal forces = new ForceTotal();
        forces.applyLinearImpulse(localImpulse);
        handle.applyForcesAndReset(forces);
    }

    static boolean anyBogeyGrounded(ServerLevel world, ServerSubLevel sub, Car car) {
        if (world == null || sub == null || car == null) {
            return false;
        }
        ServerLevel subLevel = sub.getLevel();
        for (SableTrain.Bogey bogey : car.bogeys()) {
            BlockPos cell = sub.getPlot().getCenterBlock().offset(bogey.localPos());
            if (!(subLevel.getBlockState(cell).getBlock() instanceof AbstractBogeyBlock)) {
                continue;
            }
            boolean hanging = com.lycoris.loconautics.trainphysics.spawn.WheelColliders
                    .isUpsideDown(subLevel.getBlockState(cell));
            Vector3d w = sub.logicalPose().transformPosition(
                    new Vector3d(cell.getX() + 0.5, cell.getY() + 0.5 + (hanging ? 1.5 : -1.5), cell.getZ() + 0.5),
                    new Vector3d());
            BlockPos foot = BlockPos.containing(w.x, w.y, w.z);
            if (solidAt(world, foot) || solidAt(world, hanging ? foot.above() : foot.below())) {
                return true;
            }
        }
        return false;
    }

    static boolean anyBogeyAlive(ServerSubLevel sub, Car car) {
        if (sub == null || car == null) {
            return false;
        }
        ServerLevel subLevel = sub.getLevel();
        for (SableTrain.Bogey bogey : car.bogeys()) {
            BlockPos cell = sub.getPlot().getCenterBlock().offset(bogey.localPos());
            if (subLevel.getBlockState(cell).getBlock() instanceof AbstractBogeyBlock) {
                return true;
            }
        }
        return false;
    }

    static boolean solidAt(ServerLevel world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    public static Vector3d railTangent(Car car) {
        var bogeys = car.chain();
        if (bogeys.size() >= 2) {
            Vector3d rear = bogeys.get(0).rail().center();
            Vector3d front = bogeys.get(bogeys.size() - 1).rail().center();
            if (rear != null && front != null) {
                Vector3d chord = new Vector3d(front).sub(rear);
                if (chord.lengthSquared() > 1.0e-9) {
                    return chord.normalize();
                }
            }
        }
        return forwardUnit(car.carriage());
    }

    static Vector3d forwardUnit(RailCarriage carriage) {
        Vec3 f = carriage.forward();
        Vector3d v = new Vector3d(f.x, f.y, f.z);
        return v.lengthSquared() < 1.0e-9 ? new Vector3d(1, 0, 0) : v.normalize();
    }

    static void sweepStaleState() {
        java.util.Set<UUID> live = RailGlue.sweepStaleConstraints();
        RailSnap.retain(live);
        DEAD_END.retainAll(live);
        DEAD_END_MISS.keySet().retainAll(live);
        HOP_SCAN.keySet().retainAll(live);
        SeatBlend.retain(live);
    }

    // the rig is keyed by sub level id and the id outlives the body, so a returning cart would glue to a constraint on a dead body
    public static void forgetBodyState(SableTrain train) {
        UUID subId = train.car() == null ? null : train.car().subLevelId();
        if (subId != null) {
            RailGlue.releaseConstraint(subId);
            clearCrossState(subId);
            SeatBlend.end(subId);
            WeldedBodies.release(subId);
        }
    }

    public static void clearAll() {
        DockGroups.clear();
        DockApproach.clear();
        RailGlue.clearAll();
    }

    static void clearCrossState(UUID id) {
        DEAD_END.remove(id);
        DEAD_END_MISS.remove(id);
        HOP_SCAN.remove(id);
        LAST_TRACTION_V.remove(id);
    }

    static void forgetDeadTrains() {
        java.util.Set<UUID> live = new java.util.HashSet<>();
        java.util.Set<UUID> liveSubs = new java.util.HashSet<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            live.add(train.id());
            if (train.car().subLevelId() != null) {
                liveSubs.add(train.car().subLevelId());
            }
        }
        TrainRailAdvance.retainTurnLogs(liveSubs);
        WeldedBodies.retain(liveSubs);
        ExternalForceDerail.retain(live, liveSubs);
        TrainDerailment.retain(live);
        TrainSyncCache.retain(live);
        TrainStatusChat.retain(live);
        TrainResidency.forget(live);
        StationChunkTickets.retain(live);
    }

    static void capTrainSpeed(ServerSubLevel sub, Car car, SableTrain train) {
        double capMps = TrainPropulsion.bodySpeedCapMps(train);
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        Vector3d relative = handle.getLinearVelocity(new Vector3d()).sub(railVelocityUnder(sub, car));
        Vector3d tangent = railTangent(car);
        double speed = relative.dot(tangent);
        if (Math.abs(speed) > capMps) {
            Vector3d delta = new Vector3d(tangent).mul(Math.signum(speed) * capMps - speed);
            handle.addLinearAndAngularVelocity(delta, new Vector3d());
        }
    }

    public static Vector3d railVelocityUnder(ServerSubLevel sub, Car car) {
        ServerSubLevel host = TrainRailAdvance.leadingActiveSub(car);
        if (host == null) {
            return new Vector3d();
        }
        Vector3d[] ref = TrainRailAdvance.bogeyReference(sub, car);
        Vector3d point = ref != null ? ref[0] : new Vector3d(sub.logicalPose().position());
        return TrackHostMotion.railVelocity(sub.getLevel(), host, point, new Vector3d());
    }

    static void forEachCar(SubLevelPhysicsSystem system, CarAction action) {
        if (SableTrainRegistry.isEmpty()) {
            return;
        }
        ServerLevel level = system.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        PhysicsPipeline pipeline = system.getPipeline();

        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() != level) {
                continue;
            }
            Car car = train.car();
            if (car.subLevelId() == null) {
                continue;
            }
            if (!(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
                continue;
            }
            try {
                action.run(pipeline, container, sub, train, car);
            } catch (Throwable t) {
                LoconauticsConstants.LOGGER.error("[sabletrain] error driving sub-level {}", car.subLevelId(), t);
            }
        }
    }

    @FunctionalInterface
    private interface CarAction {
        void run(PhysicsPipeline pipeline, ServerSubLevelContainer container, ServerSubLevel sub, SableTrain train, Car car);
    }

    public static Quaterniond seatPose(ServerSubLevel sub, SableTrain train, Vector3d posDst) {
        Car car = train.car();
        RailSnap.RailSeat seat = RailSnap.railSeat(sub, car);
        if (seat == null) {
            return null;
        }
        PoseTweak.Applied tweaked = PoseTweak.apply(sub, RailSnap.seatPosition(sub, seat, seat.orientation()),
                seat.orientation(), car.carriage().referenceForward(), train.poseTweak());
        posDst.set(tweaked.position());
        return tweaked.orientation();
    }

    static int massCounter = 0;

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SableTrainSyncPacket.syncAllTo(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SubLevelSplitWatcher.processPendingSplits();
        if (event.getServer().getTickCount() % 10 == 0) {
            SubLevelSplitWatcher.dropBogeylessTrains();
            SubLevelSplitWatcher.adoptNewBogeys();
        }
        com.lycoris.loconautics.trainphysics.spawn.AssembledBogeyTrains.processPending();
        com.lycoris.loconautics.trainphysics.spawn.ArticulationLinks.processPending(event.getServer());
        forgetDeadTrains();
        sweepStaleState();
        com.lycoris.loconautics.trainphysics.state.CreateTrainShadow.sync();
        com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.sweepOrphans();
        com.lycoris.loconautics.trainphysics.portal.PortalEntry.sweep();
        com.lycoris.loconautics.trainphysics.portal.PortalTwins.sweep(event.getServer());
        com.lycoris.loconautics.trainphysics.portal.PortalTwins.syncBlocks(event.getServer());
        com.lycoris.loconautics.trainphysics.portal.PortalHold.sweep(event.getServer(),
                com.lycoris.loconautics.trainphysics.portal.TrainPortalCrossing.entering());
        CarriageCollisions.tick();
        SubLevelContacts.sweep();
        com.lycoris.loconautics.trainphysics.rail.RailSeams.sweep();
        com.lycoris.loconautics.common.sable.TrackHostFrameSync.serverTick(event.getServer());
        com.lycoris.loconautics.common.turntable.TurntableRegistry.serverTick(event.getServer());
        TrainStatusChat.tickPending();
        TrainSignals.updateOccupancy();
        if (SableTrainRegistry.isEmpty()) {
            return;
        }
        boolean updateMass = (massCounter++ % 10) == 0;
        TrainStations.stationTickCounter++;
        ConsistPower.tick(updateMass);
        DockGroups.refresh();
        for (SableTrain train : SableTrainRegistry.all()) {
            try {
                TrainResidency.tick(train);
                WeldedBodies.refresh(train.level(), train.car());
                train.tickPoseSettle();
                if (train.isGhost()) {
                    continue;
                }
                // before checkderailment, a point parked at a portal plane looks exactly like a dead end to it
                com.lycoris.loconautics.trainphysics.portal.TrainPortalCrossing.tick(train);
                WheelColliders.reconcile(train);
                // sync reads isderailedofftrackbogeys, so it has to run after the tick that flips them
                TrainDerailment.check(train);
                com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.sync(train);
                TrainPropulsion.updateAxleRedstone(train);
                TrainSteering.tickSteering(train);
                train.setServoBrake(false);
                train.setFollowing(false);
                TrainBrakes.update(train);
                TrainPropulsion.applyPropulsion(train);
                AnalogControllerBlockEntity driven = ScheduleReadiness.drivenController(axleSub(train), train.id());
                SableScheduleRuntime.tick(train, driven != null ? driven : controllerIn(train));
                TrainStations.tickStation(train, operatedControllerIn(train));
                TrainStations.updateStationPresence(train);
                TrainSignals.tickSignals(train);
                ConsistFollow.apply(train);
                com.lycoris.loconautics.trainphysics.drive.observer.TrainObservers.tick(train);
                TrainPropulsion.applyDynamics(train);
                TrainResidency.holdForGhostedConsist(train);
                StationChunkTickets.update(train);
                com.lycoris.loconautics.compat.computercraft.CcControl.apply(train);
                train.tickMotion();
                TrainRunOver.tick(train);
                com.lycoris.loconautics.common.advancement.TrainAdvancements.check(train);
                train.poseTweak().advance();
                BogeyVisualUpdates.updateYaw(train);
                BogeyVisualUpdates.updateWheels(train);
                TrainSyncCache.sync(train);
            } catch (Throwable t) {
                LoconauticsConstants.LOGGER.error("[sabletrain] error ticking motion for {}", train.id(), t);
            }
        }
    }

    static double trainMass(ServerSubLevel sub) {
        MassData md = sub.getMassTracker();
        double m = (md != null && !md.isInvalid()) ? md.getMass() : 0.0;
        return m > 0.0 ? m : TrainPropulsion.liveMass(sub);
    }


    public static ServerPlayer operatorOf(SableTrain train, AnalogControllerBlockEntity controller) {
        UUID userId = controller.getCurrentUser();
        if (userId == null) {
            return null;
        }
        return train.level().getPlayerByUUID(userId) instanceof ServerPlayer sp ? sp : null;
    }

    public static AnalogControllerBlockEntity operatedControllerIn(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return null;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        AnalogControllerBlockEntity controller = analogControllerIn(sub);
        return (controller != null && controller.hasUser()) ? controller : null;
    }

    public static net.minecraft.core.Direction axleFacing(SableTrain train) {
        ServerSubLevel sub = axleSub(train);
        BearingAxleBlockEntity axle = sub == null ? null : TrainPropulsion.bearingAxleIn(sub);
        return axle == null ? null
                : com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock.front(axle.getBlockState());
    }

    public static Vec3 axleNose(SableTrain train) {
        ServerSubLevel sub = axleSub(train);
        net.minecraft.core.Direction front = axleFacing(train);
        if (sub == null || front == null) {
            return null;
        }
        Vector3d nose = sub.logicalPose().orientation()
                .transform(new Vector3d(front.getStepX(), front.getStepY(), front.getStepZ()));
        return new Vec3(nose.x, nose.y, nose.z);
    }

    public static Vec3 headingNose(SableTrain train) {
        Vec3 nose = axleNose(train);
        net.minecraft.core.Direction front = axleFacing(train);
        if (nose == null || front == null) {
            return nose;
        }
        AnalogControllerBlockEntity driver = operatedControllerIn(train);
        if (driver == null) {
            driver = ScheduleReadiness.drivenController(axleSub(train), train.id());
        }
        return driver != null && ScheduleReadiness.looksAlong(driver, front.getOpposite()) ? nose.reverse() : nose;
    }

    public static int driverRank(SableTrain train) {
        if (operatedControllerIn(train) != null) {
            return 0;
        }
        AnalogControllerBlockEntity own = controllerIn(train);
        if (own != null && own.isScheduleControlled()
                || ScheduleReadiness.drivenController(axleSub(train), train.id()) != null) {
            return 1;
        }
        return own != null && own.isLocked() ? 2 : 3;
    }

    public static Vec3 driverLook(SableTrain train) {
        ServerSubLevel sub = axleSub(train);
        if (sub == null) {
            return null;
        }
        AnalogControllerBlockEntity driver = operatedControllerIn(train);
        if (driver == null) {
            AnalogControllerBlockEntity own = controllerIn(train);
            driver = own != null && own.isScheduleControlled() ? own : ScheduleReadiness.drivenController(sub, train.id());
            if (driver == null && own != null && own.isLocked()) {
                driver = own;
            }
        }
        if (driver == null) {
            return null;
        }
        net.minecraft.core.Direction ahead = driver.getBlockState().getValue(
                com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock.FACING).getOpposite();
        Vector3d look = sub.logicalPose().orientation()
                .transform(new Vector3d(ahead.getStepX(), ahead.getStepY(), ahead.getStepZ()));
        return new Vec3(look.x, look.y, look.z);
    }

    public static ServerSubLevel axleSub(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return null;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        return sub;
    }

    public static int noseSense(SableTrain train,
                                com.simibubi.create.content.trains.entity.TravellingPoint nose) {
        Vec3 front = headingNose(train);
        if (front == null || nose == null || nose.edge == null) {
            return 1;
        }
        Vec3 tangent = RailSeams.worldDirOf(nose.edge.node1.getLocation(), nose.edge.getDirectionAt(nose.position));
        if (tangent == null) {
            return 1;
        }
        return front.x * tangent.x + front.z * tangent.z < 0.0 ? -1 : 1;
    }

    public static Vec3 scheduleDriveTangent(SableTrain train) {
        ServerSubLevel sub = com.lycoris.loconautics.common.sable.SubLevelData.resolve(
                train.level(), train.car().subLevelId());
        Vector3d[] ref = TrainRailAdvance.bogeyReference(sub, train.car());
        if (ref != null) {
            return new Vec3(ref[1].x, ref[1].y, ref[1].z);
        }
        return train.isTrackless() ? null : train.car().carriage().forward();
    }

    public static AnalogControllerBlockEntity controllerIn(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return null;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        return analogControllerIn(sub);
    }

    static AnalogControllerBlockEntity analogControllerIn(ServerSubLevel sub) {
        return SubLevelBlocks.find(sub, AnalogControllerBlockEntity.class);
    }

}