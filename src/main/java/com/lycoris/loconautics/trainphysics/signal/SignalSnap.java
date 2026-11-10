package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.station.StationLanding;

public final class SignalSnap {

    public static final double DEFAULT_STOP_OFFSET = 0.65;

    public static double stopOffset = DEFAULT_STOP_OFFSET;

    private SignalSnap() {
    }

    public static boolean canSnap(SableTrain train, double gap) {
        return StationLanding.canSnap(train, gap);
    }

    public static boolean wentPast(double gap) {
        return gap < -StationLanding.SNAP_REACH;
    }

    public static void snap(SableTrain train, double signedGap) {
        StationLanding.snapConsist(train, signedGap);
    }
}
