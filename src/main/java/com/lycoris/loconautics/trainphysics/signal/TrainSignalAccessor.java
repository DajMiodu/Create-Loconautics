package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.IEdgePointListener;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBoundary;

public final class TrainSignalAccessor {

    public static final double QUERY_RANGE = 256.0;

    private TrainSignalAccessor() {
    }

    public static boolean isHeldAtRed(SableTrain train) {
        return TrainSignals.isHeld(train.id());
    }

    private static double travelDirection(SableTrain train) {
        double speed = train.speed();
        double target = train.targetSpeed();
        if (Math.abs(speed) > 1.0e-4) {
            return Math.signum(speed);
        }
        if (Math.abs(target) > 1.0e-6) {
            return Math.signum(target);
        }
        return Double.NaN;
    }

    private static double nearer(double a, double b) {
        if (Double.isNaN(a)) {
            return b;
        }
        if (Double.isNaN(b)) {
            return a;
        }
        return Math.min(a, b);
    }

    public static double gapToRed(SableTrain train) {
        double direction = travelDirection(train);
        if (!Double.isNaN(direction)) {
            return TrainSignals.gapToRed(train, direction, QUERY_RANGE);
        }
        return nearer(TrainSignals.gapToRed(train, 1.0, QUERY_RANGE),
                TrainSignals.gapToRed(train, -1.0, QUERY_RANGE));
    }

    public record SignalAhead(SignalBoundary signal, boolean primary, double distance) {
    }

    public static double gapToAnySignal(SableTrain train) {
        SignalAhead ahead = nextSignal(train);
        return ahead == null ? Double.NaN : ahead.distance();
    }

    public static SignalAhead nextSignal(SableTrain train) {
        double direction = travelDirection(train);
        if (!Double.isNaN(direction)) {
            return scanAny(train, direction, QUERY_RANGE);
        }
        return nearer(scanAny(train, 1.0, QUERY_RANGE), scanAny(train, -1.0, QUERY_RANGE));
    }

    private static SignalAhead nearer(SignalAhead a, SignalAhead b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.distance() <= b.distance() ? a : b;
    }

    private static SignalAhead scanAny(SableTrain train, double direction, double range) {
        if (train.isDerailed() || train.isPhysics() || train.car() == null) {
            return null;
        }
        RailCarriage carriage = train.car().carriage();
        TrackGraph graph = carriage == null ? null : carriage.graph();
        TravellingPoint origin = TrainStations.nosePoint(train);
        if (graph == null || origin == null || origin.edge == null) {
            return null;
        }
        TravellingPoint scout = new TravellingPoint(
                origin.node1, origin.node2, origin.edge, origin.position, origin.upsideDown);
        SignalAhead[] found = { null };
        IEdgePointListener listener = (distance, pair) -> {
            if (!(pair.getFirst() instanceof SignalBoundary signal)) {
                return false;
            }
            found[0] = new SignalAhead(signal, signal.isPrimary(pair.getSecond().getSecond()),
                    Math.abs(distance) - SignalSnap.stopOffset);
            return true;
        };
        scout.travel(graph, direction * range,
                scout.steer(SteerDirection.NONE, carriage.upNormal()),
                listener, scout.ignoreTurns(), scout.ignorePortals());
        return found[0];
    }
}
