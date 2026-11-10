package com.lycoris.loconautics.foundation.math;

import org.joml.Vector3d;

public final class VecMath {

    private VecMath() {
    }

    public static Vector3d perpendicularUp(Vector3d forward) {
        return perpendicularUp(forward, new Vector3d(0, 1, 0));
    }

    public static Vector3d perpendicularUp(Vector3d forward, Vector3d upWorld) {
        Vector3d up = new Vector3d(upWorld).sub(new Vector3d(forward).mul(upWorld.dot(forward)), new Vector3d());
        if (up.lengthSquared() < 1.0e-6) {
            Vector3d alt = new Vector3d(1, 0, 0);
            up = alt.sub(new Vector3d(forward).mul(alt.dot(forward)), new Vector3d());
        }
        return up.normalize();
    }
}
