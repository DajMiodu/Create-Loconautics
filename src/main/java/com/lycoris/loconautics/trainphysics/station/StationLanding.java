package com.lycoris.loconautics.trainphysics.station;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainBrakes;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class StationLanding {

    public static final double DECELERATION = 0.003;
    public static final double LANDING_RANGE = 0.5;
    public static final double COARSE_CRAWL = 0.05;
    public static final double LANDING_GAIN = COARSE_CRAWL / LANDING_RANGE;
    public static final double LANDING_MIN = 0.008;
    public static final double PARK_SPEED = 0.03;
    public static final double SNAP_SPEED = 0.01;

    public static final double WHEEL_TOUCH_REACH = 0.5;

    private static final double SNAP_STEP = 0.5;
    private static final int SNAP_PASSES = 3;
    private static final double SNAP_EPSILON = 0.01;
    private static final double SNAP_PROBE = 0.25;
    private static final double MAX_SNAP_GAP = 64.0;

    public static final double DEFAULT_PARK_OFFSET = -0.586;

    public static double parkOffset = DEFAULT_PARK_OFFSET;
    public static final double SNAP_REACH = 1.0 / 32.0;

    public static final double APPROACH_MARGIN = 0.6;
    private static final double GENTLE_DECEL_SCALE = 0.6;

    public static double approachSpeed(SableTrain train, double gap) {
        return approachSpeed(train, gap, decelerationFor(train));
    }

    // the sqrt curve gets too steep at the end for a heavy train to follow
    public static double gentleApproachSpeed(SableTrain train, double gap) {
        return approachSpeed(train, gap, decelerationFor(train) * GENTLE_DECEL_SCALE);
    }

    private static double approachSpeed(SableTrain train, double gap, double decel) {
        if (gap > LANDING_RANGE) {
            double mag = Math.min(Math.sqrt(2.0 * decel * gap),
                    TrainPropulsion.gearedTopBpt(train));
            return Math.max(mag, COARSE_CRAWL);
        }
        return Math.max(LANDING_MIN, Math.min(gap * LANDING_GAIN, COARSE_CRAWL));
    }

    public static double decelerationFor(SableTrain train) {
        double limit = brakeLimitBpt2();
        return Math.min(limit, Math.max(DECELERATION, limit * APPROACH_MARGIN));
    }

    public static double hardStopDistance(SableTrain train) {
        double v = Math.abs(train.speed());
        return (v * v) / (2.0 * brakeLimitBpt2());
    }

    public static double emergencyDecel() {
        return brakeLimitBpt2();
    }

    private static double brakeLimitBpt2() {
        return Math.max(1.0e-6, TrainPropulsion.brakeDecelLimit() * TrainPropulsion.MPS2_TO_BPT2);
    }

    public static int powerNotches(SableTrain train, double mag) {
        double cap = TrainPropulsion.gearedTopBpt(train);
        if (cap <= 0.0 || Math.abs(train.speed()) >= mag) {
            return 0;
        }
        return Math.max(1, (int) Math.round(15.0 * mag / cap));
    }

    // reversing back onto a stop that is now behind is the hunting that never settles
    public static boolean wentPast(double signedError, int sense) {
        return sense != 0 && signedError * sense < 0.0;
    }

    public static boolean canSnap(SableTrain train, double gap) {
        double speed = Math.abs(train.speed());
        if (gap > SNAP_REACH && gap > speed && speed > SNAP_SPEED) {
            return false;
        }
        return gap - speed < SNAP_REACH;
    }

    public static boolean wheelOnMark(double gap) {
        return Math.abs(gap) <= WHEEL_TOUCH_REACH;
    }

    public static void snapConsist(SableTrain train, double signedGap) {
        snapConsist(train, signedGap, WHEEL_TOUCH_REACH);
    }

    public static void snapConsist(SableTrain train, double signedGap, double keepReach) {
        snapConsist(train, ConsistNose.of(train), signedGap, keepReach);
    }

    public static void snapConsist(SableTrain train, ConsistNose.Nose end, double signedGap, double keepReach) {
        if (Math.abs(signedGap) <= keepReach) {
            signedGap = 0.0;
        }
        double remaining = signedGap;
        int flip = ConsistNose.frameFlip(train, end);
        for (int pass = 0; pass < SNAP_PASSES; pass++) {
            Vec3 forward = gapForward(end);
            Vec3 from = noseWheel(end);
            double asked = remaining;
            applyConsist(train, remaining * flip);
            Vec3 landed = noseWheel(end);
            if (forward == null || from == null || landed == null) {
                break;
            }
            remaining -= landed.subtract(from).dot(forward);
            if (Math.abs(remaining) <= SNAP_EPSILON) {
                break;
            }
            if (!Double.isFinite(remaining) || Math.abs(remaining) > MAX_SNAP_GAP) {
                LoconauticsConstants.LOGGER.warn("[snapguard] train {} snap left {} blocks after pass {}, dropped",
                        shortId(train.id()), remaining, pass);
                break;
            }
        }
    }

    private static void applyConsist(SableTrain train, double signedGap) {
        applySnap(train, signedGap);
        ServerLevel level = train.level();
        UUID myId = train.car() == null ? null : train.car().subLevelId();
        if (level == null || myId == null) {
            return;
        }
        Vec3 leadForward = SableTrainDriver.scheduleDriveTangent(train);
        java.util.Set<UUID> mates = train.isGhost() ? train.consistCache() : TrainConsist.connectedSubLevels(level, myId);
        for (UUID id : mates) {
            if (id.equals(myId)) {
                continue;
            }
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other == null || other.isDerailed() || other.car() == null || other.isGhost() && !train.isGhost()) {
                continue;
            }
            Vec3 forward = SableTrainDriver.scheduleDriveTangent(other);
            double sense = leadForward == null || forward == null || forward.dot(leadForward) >= 0.0 ? 1.0 : -1.0;
            if (other.isGhost()) {
                advanceInSteps(other.car(), signedGap * sense);
                other.setSpeed(0.0);
                other.setTargetSpeed(0.0);
                continue;
            }
            applySnap(other, signedGap * sense);
        }
    }

    public static void applySnap(SableTrain train, double signedGap) {
        advanceInSteps(train.car(), signedGap);
        killMotion(train);
        train.setSpeed(0.0);
        train.setTargetSpeed(0.0);
        TrainBrakes.holdSoft(train);
    }

    // one long rail step reads as a stretched coupling and derails the car
    private static void advanceInSteps(SableTrain.Car car, double gap) {
        if (!Double.isFinite(gap) || Math.abs(gap) > MAX_SNAP_GAP) {
            LoconauticsConstants.LOGGER.warn("[snapguard] snap of {} blocks refused", gap);
            return;
        }
        double left = gap;
        while (Math.abs(left) > SNAP_STEP) {
            double step = Math.copySign(SNAP_STEP, left);
            TrainRailAdvance.advanceRail(car, step);
            left -= step;
        }
        TrainRailAdvance.advanceRail(car, left);
    }

    private static Vec3 gapForward(ConsistNose.Nose end) {
        RailCarriage carriage = end.rail();
        TravellingPoint nose = end.point();
        if (carriage == null || carriage.graph() == null || nose == null || nose.edge == null) {
            return null;
        }
        Vec3 here = carriage.worldPointOf(nose);
        Vec3 ahead = travelled(carriage, nose, SNAP_PROBE);
        if (here == null || ahead == null) {
            return null;
        }
        Vec3 along = ahead.subtract(here);
        return along.lengthSqr() < 1.0e-9 ? null : along.normalize();
    }

    private static Vec3 noseWheel(ConsistNose.Nose end) {
        RailCarriage carriage = end.rail();
        TravellingPoint nose = end.point();
        if (carriage == null || carriage.graph() == null || nose == null || nose.edge == null) {
            return null;
        }
        return carriage.worldPointOf(nose);
    }

    private static Vec3 travelled(RailCarriage carriage, TravellingPoint from, double distance) {
        TravellingPoint scout = new TravellingPoint(
                from.node1, from.node2, from.edge, from.position, from.upsideDown);
        scout.travel(carriage.graph(), distance,
                scout.steer(SteerDirection.NONE, carriage.upNormal()),
                scout.ignoreEdgePoints(), scout.ignoreTurns(), scout.ignorePortals());
        return carriage.worldPointOf(scout);
    }

    private static void killMotion(SableTrain train) {
        SableTrain.Car car = train.car();
        ServerSubLevel sub = car == null ? null : SubLevelData.resolve(train.level(), car.subLevelId());
        if (sub != null) {
            SubLevelData.stopLinear(sub);
        }
    }

    private StationLanding() {
    }
}
