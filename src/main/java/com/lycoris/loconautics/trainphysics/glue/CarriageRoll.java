package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.coupling.CouplerTwist;
import com.lycoris.loconautics.trainphysics.spawn.WheelColliders;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CarriageRoll {

    private CarriageRoll() {
    }

    // creates rail model spans 0 to 0.2836 above the bezier point, the car anchor sits one block above it
    public static final double RAIL_TOP = 0.2836;
    public static final double SEAT_DROP = 1.0 - RAIL_TOP;
    public static final double FLANGE = 0.125;
    public static final double LIFT_RANGE = 4.0;
    public static final double LIFT_EPS = 0.02;
    public static final double PHI_EPS = Math.toRadians(0.2);
    public static final long SETTLE_TICKS = 20L;
    public static final double SUSPENSION_HZ = 1.2;
    public static final int UNGLUE_TICKS = 3;
    public static final double FALL_MARGIN = Math.toRadians(5.0);
    public static final String LOCK_ARTICULATED = "articulated";
    private static final double MIN_GRAVITY = 0.5;

    public static final TagKey<Block> NO_WHEEL_LIFT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(LoconauticsConstants.MOD_ID, "no_wheel_lift"));

    // one condition that has to hold on unglueticks game ticks in a row, counted once per tick
    public static final class Hold {
        private int count;
        private long tick = Long.MIN_VALUE;

        public boolean update(boolean condition, long gameTime) {
            if (!condition) {
                count = 0;
                tick = Long.MIN_VALUE;
                return false;
            }
            if (tick != gameTime) {
                count++;
                tick = gameTime;
            }
            return count >= UNGLUE_TICKS;
        }

        public void reset() {
            count = 0;
            tick = Long.MIN_VALUE;
        }
    }

    public static final class Roll {
        public volatile boolean rolling;
        public volatile String lock = "none";
        public volatile double phi;
        public volatile double omegaR;
        public volatile double heave;
        public volatile double rise;
        public volatile double preUp;
        public volatile boolean preValid;
        public volatile long curveUntil = Long.MIN_VALUE / 2;
        public volatile double curveBase;
        public volatile boolean curveClamped;
        public volatile int side;
        public volatile double lean;
        public volatile double offset;
        public volatile double height;
        public volatile double tip;
        public volatile double unglue;
        public volatile double predicted;
        public volatile double speed;
        public volatile double torque;
        public volatile double relief;
        public volatile double curvature;
        public volatile Vector3d axis = new Vector3d(0.0, 0.0, 1.0);
        public volatile Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        public volatile Quaterniond seat = new Quaterniond();
        public volatile double hingeK;
        public volatile double hingeI;
        public volatile double half;
        public volatile double mass;
        public volatile String primaryLock;
        public volatile double couplerTorque;
        public volatile double couplerTwist;
        public volatile double suspension;
        public volatile double suspensionRate;
        public volatile double suspensionSent;
        public volatile long suspensionSentTick = -1000L;
        public volatile double lastMass = -1.0;
        public final Vector3d lastCenter = new Vector3d();
        public volatile long settleUntil;
        public volatile long lastDriveTick = Long.MIN_VALUE;
        public volatile double[] y = new double[0];
        public volatile int lifted;
        public final Hold rolledOff = new Hold();
        public final Hold liftedOff = new Hold();

        public boolean settling(long gameTime) {
            return gameTime < settleUntil;
        }

        public boolean curveHeld(long gameTime) {
            return gameTime < curveUntil;
        }

        public boolean allLifted() {
            double[] wheels = y;
            if (wheels.length == 0) {
                return false;
            }
            for (double wheel : wheels) {
                if (wheel <= LIFT_EPS) {
                    return false;
                }
            }
            return true;
        }

        public boolean allBeyondFlange() {
            double[] wheels = y;
            if (wheels.length == 0) {
                return false;
            }
            for (double wheel : wheels) {
                if (wheel <= FLANGE) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final ConcurrentHashMap<UUID, Roll> STATE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> ROLLING_THIS_STEP = new ConcurrentHashMap<>();

    public static Roll of(UUID id) {
        return STATE.computeIfAbsent(id, key -> new Roll());
    }

    public static Roll rollOf(ServerSubLevel sub) {
        return sub == null ? null : STATE.get(sub.getUniqueId());
    }

    public static Roll rollById(UUID id) {
        return id == null ? null : STATE.get(id);
    }

    // wheellift is temporary, it goes with the fix confirmation
    public static final boolean TRACE = true;
    private static final ConcurrentHashMap<UUID, Long> LAST_TRACE = new ConcurrentHashMap<>();

    public static boolean traceDue(UUID id, long gameTime) {
        if (!TRACE) {
            return false;
        }
        Long last = LAST_TRACE.get(id);
        if (last != null && gameTime - last < 10L) {
            return false;
        }
        LAST_TRACE.put(id, gameTime);
        return true;
    }

    public static String primaryLockOf(UUID id) {
        Roll roll = STATE.get(id);
        return roll == null ? null : roll.primaryLock;
    }

    public static boolean isRolling(UUID id) {
        Roll roll = STATE.get(id);
        return roll != null && roll.rolling;
    }

    public static boolean wheelsLifted(UUID id) {
        Roll roll = STATE.get(id);
        return roll != null && roll.rolling && roll.allLifted();
    }

    public static void markRolling(UUID id) {
        ROLLING_THIS_STEP.put(id, Boolean.TRUE);
    }

    public static boolean consumeRolling(UUID id) {
        return ROLLING_THIS_STEP.remove(id) != null;
    }

    public static String lockReason(ServerSubLevel sub, Car group, boolean docked, SableTrain train) {
        if (train.hasOffTrackBogey()) {
            return "pivot";
        }
        if (group.carriage() == null) {
            return "trackless";
        }
        if (docked) {
            return "docked";
        }
        var chain = group.chain();
        if (chain.size() < 2) {
            return "single_bogey";
        }
        if (group.carriage().isOnMonorail()) {
            return "monorail";
        }
        for (SableTrain.Bogey bogey : chain) {
            if (bogey.rail().isOnMonorail()) {
                return "monorail";
            }
        }
        for (SableTrain.Bogey bogey : group.bogeys()) {
            BlockState state = sub.getLevel().getBlockState(sub.getPlot().getCenterBlock().offset(bogey.localPos()));
            if (state.is(NO_WHEEL_LIFT)) {
                return "no_wheel_lift";
            }
            if (WheelColliders.isUpsideDown(state)) {
                return "upside_down";
            }
        }
        return CouplerTwist.articulatedLock(sub.getUniqueId());
    }

    public static boolean hasGravity(double alongUp) {
        return alongUp >= MIN_GRAVITY;
    }

    public static void noteLock(UUID id, String reason) {
        Roll roll = of(id);
        String next = reason == null ? "none" : reason;
        if (!next.equals(roll.lock)) {
            LoconauticsConstants.LOGGER.info("[wheellift] car={} lock {} -> {}", id.toString().substring(0, 8),
                    roll.lock, next);
            roll.lock = next;
        }
        roll.rolling = reason == null;
        roll.primaryLock = reason == null || reason.equals(LOCK_ARTICULATED) || reason.equals("released") ? null
                : reason;
        if (reason != null) {
            roll.rolledOff.reset();
            roll.liftedOff.reset();
        }
    }

    // a car that was not rolling on the last substep, or whose last substep is more than a tick old, starts settling
    public static void beginStep(Roll roll, long gameTime) {
        boolean fresh = !roll.rolling || gameTime - roll.lastDriveTick > 1L;
        roll.lastDriveTick = gameTime;
        if (fresh) {
            roll.settleUntil = gameTime + SETTLE_TICKS;
            roll.side = 0;
            roll.rolledOff.reset();
            roll.liftedOff.reset();
            LoconauticsConstants.LOGGER.info("[wheellift] settle window opened until tick {}", roll.settleUntil);
        }
    }

    // the hinge side keeps its sign until the roll crosses to the other side by more than phieps
    public static int updateSide(Roll roll, double phi) {
        int side = roll.side;
        if (side == 0) {
            if (Math.abs(phi) > PHI_EPS) {
                side = phi > 0.0 ? 1 : -1;
            }
        } else if (phi * side < -PHI_EPS) {
            side = -side;
        }
        roll.side = side;
        return side;
    }

    public record Ready(String reason, WheelSupports.Prepared prepared) {
    }

    // everything the rolling glue needs, checked before any glue call so a substep never switches mode twice
    public static Ready rollReady(ServerSubLevel sub, Car car, RollFrame frame, PoseTweak.Applied seated) {
        MassData md = sub.getMassTracker();
        if (frame == null || md == null || md.isInvalid() || RigidBodyHandle.of(sub) == null
                || car.chain().size() < 2) {
            return new Ready("no_geometry", null);
        }
        WheelSupports.Prepared prepared = WheelSupports.prepare(sub, car.chain(), frame, seated);
        if (prepared == null) {
            return new Ready("no_geometry", null);
        }
        double gN = -DimensionPhysicsData.getGravity(sub.getLevel()).dot(frame.up);
        if (!hasGravity(gN)) {
            return new Ready("no_gravity", null);
        }
        return new Ready(null, prepared);
    }

    // swing and twist split about the bodys own forward axis, positive is a right hand turn about the chord
    public static double twist(org.joml.Quaterniond seat, org.joml.Quaterniond body, Vector3d forward0) {
        org.joml.Quaterniond r = new org.joml.Quaterniond(seat).invert().mul(body);
        double p = r.x * forward0.x + r.y * forward0.y + r.z * forward0.z;
        double phi = 2.0 * Math.atan2(p, r.w);
        if (phi > Math.PI) {
            phi -= 2.0 * Math.PI;
        } else if (phi < -Math.PI) {
            phi += 2.0 * Math.PI;
        }
        return phi;
    }

    // where gravity alone would tip the car, for the log and cc only, nothing derails on it
    public static double tippingAngle(double halfGauge, double offset, double height, double lean, double cant) {
        return Math.atan2(halfGauge - lean * offset, Math.max(height, 1.0e-3)) - cant;
    }

    // the most the hinge holds, in radians
    public static double holdAngle() {
        return Math.toRadians(LoconauticsConfig.DERAIL_WHEEL_LIFT_MAX_DEGREES.get());
    }

    // steady curve lean is m v2 k h less the gravity hold, over the hinge stiffness, so mass cancels
    // and the speed that keeps it under a share of the fast unglue angle is the root of hold plus hinge over k h
    public static double safeCurveSpeed(Roll roll, double gN, double curvature, double share) {
        double k = Math.abs(curvature);
        if (k < 1.0e-6 || roll.height < 0.05 || roll.half <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double phiRef = Math.toRadians(LoconauticsConfig.DERAIL_WHEEL_LIFT_DEGREES.get());
        double phiSafe = share * Math.min(Math.toRadians(LoconauticsConfig.DERAIL_ROLL_UNGLUE_DEGREES.get()),
                holdAngle());
        double hold = Math.max(0.0, gN * (roll.half - Math.abs(roll.offset)));
        double hinge = gN * roll.half * phiSafe / phiRef;
        return Math.sqrt((hold + hinge) / (k * roll.height));
    }

    // 0 at the slow roll speed and below, 1 at the fast one and above, smooth between
    public static double speedBlend(double speed) {
        double slow = LoconauticsConfig.DERAIL_ROLL_SLOW_SPEED.get();
        double fast = Math.max(slow + 1.0e-3, LoconauticsConfig.DERAIL_ROLL_FAST_SPEED.get());
        double t = Math.max(0.0, Math.min(1.0, (Math.abs(speed) - slow) / (fast - slow)));
        return t * t * (3.0 - 2.0 * t);
    }

    // slow cars come off only past the hinge ceiling plus a margin, fast cars at the flange limit
    public static double unglueAngle(double speed) {
        double blend = speedBlend(speed);
        double slowLimit = holdAngle() + FALL_MARGIN;
        double fastLimit = Math.toRadians(LoconauticsConfig.DERAIL_ROLL_UNGLUE_DEGREES.get());
        return slowLimit + blend * (fastLimit - slowLimit);
    }

    // static lean on one hinge side, infinite when the car cannot stand on that rail
    public static double predictedLift(double gN, double gSide, double halfGauge, double offset, double height,
                                       double phiRef, int side) {
        double moment = gN * (side * offset - halfGauge) + gSide * height;
        double den = gN * (halfGauge - phiRef * height);
        if (den <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return phiRef * Math.max(0.0, moment) / den;
    }

    // the worst of both hinge sides for a car at rest, using the shared formula
    public static double predictedLift(Vector3d gravity, Vector3d up, Vector3d lateral, double halfGauge,
                                       double offset, double height) {
        double gN = -gravity.dot(up);
        double phiRef = Math.toRadians(LoconauticsConfig.DERAIL_WHEEL_LIFT_DEGREES.get());
        double worst = 0.0;
        for (int side = -1; side <= 1; side += 2) {
            worst = Math.max(worst,
                    predictedLift(gN, gravity.dot(lateral) * side, halfGauge, offset, height, phiRef, side));
        }
        return worst;
    }

    // body lean on the springs, a spring damper toward a share of the max lean set by how far the load sits off center
    public static void stepSuspension(Roll roll, double halfGauge, double dt) {
        double max = Math.toRadians(LoconauticsConfig.SUSPENSION_LEAN_DEGREES.get());
        double target = max * Math.max(-1.0, Math.min(1.0, roll.offset / Math.max(halfGauge, 1.0e-3)));
        double omega = 2.0 * Math.PI * SUSPENSION_HZ;
        double zeta = LoconauticsConfig.DERAIL_WHEEL_LIFT_DAMPING.get();
        roll.suspensionRate += (omega * omega * (target - roll.suspension) - 2.0 * zeta * omega * roll.suspensionRate) * dt;
        roll.suspension += roll.suspensionRate * dt;
    }

    public static void release(UUID id) {
        STATE.remove(id);
        ROLLING_THIS_STEP.remove(id);
    }

    public static void retain(java.util.Set<UUID> live) {
        STATE.keySet().retainAll(live);
        ROLLING_THIS_STEP.keySet().retainAll(live);
        LAST_TRACE.keySet().retainAll(live);
    }

    public static void clearAll() {
        STATE.clear();
        ROLLING_THIS_STEP.clear();
        LAST_TRACE.clear();
    }
}
