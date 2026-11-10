package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.grab.GrabConstraint;
import com.lycoris.loconautics.common.grab.GrabTarget;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ScrewLinkHook extends BoxPhysicsObject implements GrabTarget {

    private static final double HALF_EXTENT = 0.125;
    private static final double MASS = 8.0;

    private final Map<UUID, GrabConstraint> grabbers = new HashMap<>();

    public ScrewLinkHook(Vector3dc at) {
        super(new Pose3d(new Vector3d(at), new Quaterniond(), new Vector3d(), new Vector3d(1.0)),
                new Vector3d(HALF_EXTENT), MASS);
    }

    @Override
    public void updateGrab(UUID player, float range, boolean rotating, Quaterniondc orientation) {
        GrabConstraint c = grabbers.get(player);
        if (c == null) {
            c = new GrabConstraint(player, range);
            grabbers.put(player, c);
        } else {
            c.setScrollDistance(range);
        }
        c.setRotation(rotating, orientation);
    }

    @Override
    public Map<UUID, GrabConstraint> grabbers() {
        return grabbers;
    }

    @Override
    public Vector3d getGrabCenter() {
        return new Vector3d(getPose().position());
    }

    @Override
    public Vector3d getGrabAnchor() {
        return new Vector3d();
    }
}
