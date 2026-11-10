package com.lycoris.loconautics.common.grab;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public interface GrabTarget {

    float LINEAR_STIFFNESS = 240.0f;
    float LINEAR_DAMPING = 30.0f;
    float ANGULAR_DAMPING = 4.5f;
    float MAX_FORCE = 120.0f;

    float MIN_GRAB_DISTANCE = 1.0f;
    float MAX_GRAB_DISTANCE = 5.0f;

    void updateGrab(UUID player, float range, boolean rotating, Quaterniondc orientation);

    Map<UUID, GrabConstraint> grabbers();

    Vector3d getGrabCenter();

    default Vector3d getGrabAnchor() {
        return getGrabCenter();
    }

    default float rotateMaxForce() {
        return MAX_FORCE;
    }

    default boolean isGrabbed() {
        return !grabbers().isEmpty();
    }

    default boolean canGrab() {
        return true;
    }

    default void stopGrab(UUID player) {
        GrabConstraint c = grabbers().remove(player);
        if (c != null) {
            c.removeJoint();
        }
    }

    default void clearGrabs() {
        grabbers().values().forEach(GrabConstraint::removeJoint);
        grabbers().clear();
    }

    default void driveGrabs(PhysicsPipelineBody body, ServerLevel level) {
        if (grabbers().isEmpty()) {
            return;
        }
        Vector3d grabCenter = getGrabCenter();
        Vector3d grabAnchor = getGrabAnchor();
        Iterator<Map.Entry<UUID, GrabConstraint>> it = grabbers().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GrabConstraint> e = it.next();
            if (level.getPlayerByUUID(e.getKey()) == null) {
                e.getValue().removeJoint();
                it.remove();
                continue;
            }
            e.getValue().physicsTick(body, level, grabCenter, grabAnchor, rotateMaxForce());
        }
    }
}
