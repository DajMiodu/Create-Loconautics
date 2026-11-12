package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.brakes.HandbrakeBlock;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class TrainBrakes {

    public static final double BRAKE_DECEL = 60.0;

    private TrainBrakes() {
    }

    public static void holdSoft(SableTrain train) {
        train.armBrakePulse();
    }

    public static void update(SableTrain train) {
        if (train.isDerailed() || train.isPhysics()) {
            train.setBraked(false);
            train.setBrakeHard(false);
            train.setBrakeHold(false);
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        SableTrain.Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return;
        }
        train.tickBrakePulse();

        double hand = handbrakeStrength(sub);
        train.setHandbrake(hand);
        double handScale = Math.min(1.0, hand / Math.max(1.0, rollingWeightUnclamped(sub)));
        boolean locked = handScale * BRAKE_DECEL > WHEEL_GRIP_DECEL;
        if (locked) {
            handScale *= SKID_FACTOR;
        }
        ConsistBrake.Demand consist = ConsistBrake.demandFor(train);
        double pipe = Math.min(1.0, consist.decel() / BRAKE_DECEL);
        // a schedule hold must not full brake here or an arrived train never pulls away
        double soft = Math.max(pipe, train.brakePulseActive() ? 1.0 : 0.0);
        double scale = Math.max(handScale, soft);

        train.setBrakeScale(scale);
        train.setBraked(scale > 0.0);
        // the handbrake never cuts drive, the engine pulls against it and its drag shows up as axle stress instead
        train.setBrakeHold(consist.hold() || train.brakePulseActive());
        train.setBrakeHard(locked);
    }

    static final double WHEEL_GRIP_DECEL = 30.0;
    static final double SKID_FACTOR = 0.7;

    static final double HANDBRAKE_STRESS_WEIGHT = 4.0;

    public static double handbrakeLoadMass(SableTrain train) {
        return train.handbrake() * LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get() * HANDBRAKE_STRESS_WEIGHT;
    }

    static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.3f", d);
    }

    private static final int BRAKE_SCAN_TICKS = 10;

    private record BrakeScan(long tick, List<BlockPos> positions) {
    }

    private static final Map<ServerSubLevel, BrakeScan> BRAKE_SCANS = new WeakHashMap<>();

    public static double handbrakeStrength(ServerSubLevel sub) {
        ServerLevel subLevel = sub.getLevel();
        long now = subLevel.getGameTime();
        BrakeScan scan = BRAKE_SCANS.get(sub);
        if (scan == null || now - scan.tick() >= BRAKE_SCAN_TICKS || now < scan.tick()) {
            List<BlockPos> found = new ArrayList<>();
            SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
                if (state.getBlock() instanceof HandbrakeBlock) {
                    found.add(pos.immutable());
                }
                return true;
            });
            scan = new BrakeScan(now, found);
            BRAKE_SCANS.put(sub, scan);
        }
        int best = 0;
        for (BlockPos pos : scan.positions()) {
            BlockState state = subLevel.getBlockState(pos);
            if (state.getBlock() instanceof HandbrakeBlock && state.getValue(HandbrakeBlock.POWERED)) {
                best = Math.max(best, subLevel.getBestNeighborSignal(pos));
            }
            if (best >= 15) {
                break;
            }
        }
        double fraction = best / 15.0;
        return fraction * fraction;
    }

    private static final double MIN_ROLLING_WEIGHT = 0.25;
    private static final double MAX_ROLLING_WEIGHT = 4.0;

    // weighted, because resistance the same for every car makes a loaded one coast as far as an empty one and
    public static double rollingScale(SableTrain train, ServerSubLevel sub) {
        if (train.isPowered() || train.isDerailed() || train.isFollowing()) {
            return 0.0;
        }
        return Math.min(1.0,
                LoconauticsConfig.DYNAMICS_ROLLING_RESISTANCE.get() * rollingWeight(sub) / BRAKE_DECEL);
    }

    private static double rollingWeight(ServerSubLevel sub) {
        return Math.max(MIN_ROLLING_WEIGHT, Math.min(MAX_ROLLING_WEIGHT, rollingWeightUnclamped(sub)));
    }

    private static double rollingWeightUnclamped(ServerSubLevel sub) {
        MassData md = sub == null ? null : sub.getMassTracker();
        double mass = (md != null && !md.isInvalid()) ? md.getMass() : 0.0;
        if (mass <= 0.0) {
            return 1.0;
        }
        return mass / LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get();
    }

    private static final double SLOPE_RELIEF_DOWNHILL_SPEED = 0.05;

    public static void relieveSlopeGravity(ServerSubLevel sub, Vector3d railPoint, Vector3d tangent, double dt) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        double along = DimensionPhysicsData.getGravity(sub.getLevel()).dot(tangent);
        if (Math.abs(along) < 1.0e-6) {
            return;
        }
        double rolling = handle.getLinearVelocity(new Vector3d()).dot(tangent);
        if (rolling * along > SLOPE_RELIEF_DOWNHILL_SPEED) {
            return;
        }
        MassData md = sub.getMassTracker();
        double mass = (md != null && !md.isInvalid()) ? md.getMass() : 1.0;
        Vector3d worldImpulse = new Vector3d(tangent).mul(-mass * along * dt);
        Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation())
                .conjugate().transform(worldImpulse, new Vector3d());
        ForceTotal forces = new ForceTotal();
        forces.applyLinearImpulse(localImpulse);
        handle.applyForcesAndReset(forces);
        ForceTrace.impulse(sub, ForceSource.SLOPE_RELIEF, worldImpulse, railPoint,
                "along rail gravity " + fmt(along) + " mass=" + fmt(mass));
    }

    public static void applyResistance(ServerSubLevel sub, ServerSubLevel trackSub, Vector3d railPoint,
                                       Vector3d tangent, double dt, double scale, ForceSource source) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null || scale <= 0.0) {
            return;
        }
        Vector3d vel = handle.getLinearVelocity(new Vector3d());
        double vTan = TrackHostMotion.relativeAlongTrack(sub.getLevel(), trackSub, railPoint, vel, tangent);
        if (Math.abs(vTan) < 1.0e-6) {
            return;
        }
        MassData md = sub.getMassTracker();
        double mass = (md != null && !md.isInvalid()) ? md.getMass() : 1.0;
        double decel = Math.min(BRAKE_DECEL * scale, Math.abs(vTan) / dt);
        Vector3d worldImpulse = new Vector3d(tangent).mul(-mass * decel * dt * Math.signum(vTan));
        Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation())
                .conjugate().transform(worldImpulse, new Vector3d());
        ForceTotal forces = new ForceTotal();
        forces.applyLinearImpulse(localImpulse);
        handle.applyForcesAndReset(forces);
        com.lycoris.loconautics.content.knuckle.KnuckleRailCoupler.noteExternal(sub.getUniqueId(),
                -decel * dt * Math.signum(vTan));
        ForceTrace.impulse(sub, source, worldImpulse, railPoint,
                "scale=" + fmt(scale) + " decel=" + fmt(decel) + " of " + fmt(BRAKE_DECEL)
                        + " against vTan=" + fmt(vTan) + " mass=" + fmt(mass));
        TrackHostMotion.pushHost(trackSub, railPoint, worldImpulse);
    }
}
