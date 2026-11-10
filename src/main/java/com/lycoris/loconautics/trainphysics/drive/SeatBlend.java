package com.lycoris.loconautics.trainphysics.drive;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public final class SeatBlend {
    private SeatBlend() {
    }

    static final java.util.Map<UUID, Vector3d> SEAT_BLEND_POS = new java.util.HashMap<>();
    static final java.util.Map<UUID, Quaterniond> SEAT_BLEND_Q = new java.util.HashMap<>();
    static final double SEAT_BLEND_RATE = 0.12;
    static final double SEAT_BLEND_DONE = 0.02;
    static final double SEAT_BLEND_MAX = 2.0;

    public static void begin(ServerSubLevel sub, Vector3d seatPos, Quaterniond seatQ) {
        UUID id = sub.getUniqueId();
        Vector3d off = new Vector3d(sub.logicalPose().position()).sub(seatPos);
        if (off.length() > SEAT_BLEND_MAX) {
            end(id);
            return;
        }
        SEAT_BLEND_POS.put(id, off);
        SEAT_BLEND_Q.put(id, new Quaterniond(seatQ).invert()
                .mul(new Quaterniond(sub.logicalPose().orientation())).normalize());
    }

    public static void end(UUID id) {
        SEAT_BLEND_POS.remove(id);
        SEAT_BLEND_Q.remove(id);
    }

    public static Quaterniond q(UUID id, Quaterniond q, boolean decay) {
        Quaterniond qErr = SEAT_BLEND_Q.get(id);
        if (qErr == null) {
            return q;
        }
        if (decay) {
            SEAT_BLEND_POS.get(id).mul(1.0 - SEAT_BLEND_RATE);
            qErr.slerp(new Quaterniond(), SEAT_BLEND_RATE).normalize();
        }
        return new Quaterniond(q).mul(qErr).normalize();
    }

    public static Vector3d pos(UUID id, Vector3d target) {
        Vector3d off = SEAT_BLEND_POS.get(id);
        if (off == null) {
            return target;
        }
        if (off.length() <= SEAT_BLEND_DONE) {
            end(id);
            return target;
        }
        return new Vector3d(target).add(off);
    }

    public static boolean active(UUID id) {
        return SEAT_BLEND_Q.containsKey(id);
    }

    public static void retain(java.util.Set<UUID> live) {
        SEAT_BLEND_POS.keySet().retainAll(live);
        SEAT_BLEND_Q.keySet().retainAll(live);
    }
}
