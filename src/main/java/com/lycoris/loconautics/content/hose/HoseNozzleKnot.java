package com.lycoris.loconautics.content.hose;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HoseNozzleKnot {

    private static final float BACK_OFF = -2.0f / 16.0f;

    private HoseNozzleKnot() {
    }

    // the knot model rises from the pivot toward the hose, the last segment points into the nozzle
    public static Quaternionf atNozzle(Quaternionf lastSegment) {
        return new Quaternionf(lastSegment).rotateX((float) Math.PI);
    }

    // slides the knot along the hose, negative is toward the nozzle, the hose end itself stays put
    public static Vector3f backOff(Quaternionf atNozzle) {
        return new Vector3f(0.0f, BACK_OFF, 0.0f).rotate(atNozzle);
    }
}
