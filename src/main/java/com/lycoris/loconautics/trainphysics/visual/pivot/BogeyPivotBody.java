package com.lycoris.loconautics.trainphysics.visual.pivot;


import org.joml.Quaterniond;
import org.joml.Vector3d;


public final class BogeyPivotBody {

    public static final double PIVOT_STIFFNESS = 300.0;
    public static final double PIVOT_DAMPING = 40.0;

    private static final double SNAP_ANGLE = Math.toRadians(70.0);
    private static final double MAX_OMEGA = 30.0;
    // slices dt into fixed steps since explicit euler pd is only stable for small dt, and low sable rates grow dt large.
    private static final double FIXED_SLICE = 1.0 / 120.0;

    private final Quaterniond orientation = new Quaterniond();
    private final Vector3d angularVelocity = new Vector3d();
    private boolean seeded;

    public void drive(double dt, Quaterniond target) {
        if (!seeded) {
            orientation.set(target);
            angularVelocity.zero();
            seeded = true;
            return;
        }

        int slices = Math.max(1, (int) Math.ceil(dt / FIXED_SLICE));
        double sliceDt = dt / slices;
        for (int i = 0; i < slices; i++) {
            if (integrateSlice(sliceDt, target)) {
                return;
            }
        }
    }

    private boolean integrateSlice(double dt, Quaterniond target) {
        Quaterniond err = new Quaterniond(target).mul(new Quaterniond(orientation).conjugate()).normalize();
        if (err.w < 0.0) {
            err.set(-err.x, -err.y, -err.z, -err.w);
        }
        double w = Math.min(1.0, Math.max(-1.0, err.w));
        double angle = 2.0 * Math.acos(w);

        if (angle > SNAP_ANGLE) {
            orientation.set(target);
            angularVelocity.zero();
            return true;
        }

        Vector3d axis = new Vector3d(err.x, err.y, err.z);
        double s = axis.length();
        Vector3d errVec = s > 1.0e-9 ? axis.mul(angle / s) : new Vector3d();

        double kp = PIVOT_STIFFNESS;
        double kd = PIVOT_DAMPING;
        Vector3d accel = new Vector3d(errVec).mul(kp).sub(new Vector3d(angularVelocity).mul(kd));
        angularVelocity.add(accel.mul(dt));
        double ov = angularVelocity.length();
        if (ov > MAX_OMEGA) {
            angularVelocity.mul(MAX_OMEGA / ov);
        }

        Vector3d dr = new Vector3d(angularVelocity).mul(dt);
        double a = dr.length();
        if (a > 1.0e-12) {
            Quaterniond dq = new Quaterniond().setAngleAxis(a, dr.x / a, dr.y / a, dr.z / a);
            dq.mul(orientation, orientation);
            orientation.normalize();
        }
        return false;
    }

    public Quaterniond currentOrientation() {
        return new Quaterniond(orientation);
    }

    public Vector3d angularVelocity(Vector3d dest) {
        return dest.set(angularVelocity);
    }
}
