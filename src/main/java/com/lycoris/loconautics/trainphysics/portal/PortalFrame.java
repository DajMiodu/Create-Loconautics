package com.lycoris.loconautics.trainphysics.portal;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;

public record PortalFrame(ResourceKey<Level> nearDim, ResourceKey<Level> farDim, TrackNodeLocation nearLoc,
                          TrackNodeLocation farLoc, Vec3 nearPoint, Vec3 farPoint, Vec3 inward, double yaw) {

    private static final double ZERO_LENGTH = 1.0e-6;

    public static PortalFrame of(TrackGraph graph, Couple<TrackNodeLocation> crossing, ResourceKey<Level> home) {
        if (graph == null || crossing == null || home == null || crossing.getFirst() == null
                || crossing.getSecond() == null || crossing.getFirst().dimension == null
                || crossing.getSecond().dimension == null) {
            return null;
        }
        boolean firstHome = crossing.getFirst().dimension.equals(home);
        TrackNodeLocation near = crossing.get(firstHome);
        TrackNodeLocation far = crossing.get(!firstHome);
        if (!near.dimension.equals(home) || far.dimension.equals(home)) {
            return null;
        }
        Vec3 away = awayFrom(graph, near);
        Vec3 out = awayFrom(graph, far);
        if (away == null || out == null) {
            return null;
        }
        Vec3 inward = away.reverse();
        double yaw = heading(out) - heading(inward);
        return new PortalFrame(near.dimension, far.dimension, near, far, near.getLocation(), far.getLocation(),
                inward, yaw);
    }

    private static Vec3 awayFrom(TrackGraph graph, TrackNodeLocation loc) {
        TrackNode node = graph.locateNode(loc);
        if (node == null) {
            return null;
        }
        for (Map.Entry<TrackNode, TrackEdge> entry : graph.getConnectionsFrom(node).entrySet()) {
            if (entry.getValue().getLength() <= ZERO_LENGTH) {
                continue;
            }
            Vec3 d = entry.getValue().getDirection(true);
            Vec3 flat = new Vec3(d.x, 0.0, d.z);
            if (flat.lengthSqr() > ZERO_LENGTH) {
                return flat.normalize();
            }
        }
        return null;
    }

    private static double heading(Vec3 d) {
        return Math.atan2(d.x, d.z);
    }

    public PortalFrame inverted() {
        return new PortalFrame(farDim, nearDim, farLoc, nearLoc, farPoint, nearPoint, dirToFar(inward).reverse(),
                -yaw);
    }

    public boolean isPortal(Couple<TrackNodeLocation> crossing) {
        if (crossing == null) {
            return false;
        }
        TrackNodeLocation a = crossing.getFirst();
        TrackNodeLocation b = crossing.getSecond();
        return (nearLoc.equals(a) && farLoc.equals(b)) || (nearLoc.equals(b) && farLoc.equals(a));
    }

    public boolean samePortal(PortalFrame other) {
        return other != null && (nearLoc.equals(other.nearLoc) && farLoc.equals(other.farLoc)
                || nearLoc.equals(other.farLoc) && farLoc.equals(other.nearLoc));
    }

    public Quaterniond rotation() {
        return new Quaterniond().rotateY(yaw);
    }

    public Vec3 toFar(Vec3 near) {
        Vector3d v = toFar(new Vector3d(near.x, near.y, near.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 toNear(Vec3 far) {
        Vector3d v = toNear(new Vector3d(far.x, far.y, far.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vector3d toFar(Vector3dc near) {
        Vector3d offset = new Vector3d(near).sub(nearPoint.x, nearPoint.y, nearPoint.z);
        return rotation().transform(offset).add(farPoint.x, farPoint.y, farPoint.z);
    }

    public Vector3d toNear(Vector3dc far) {
        Vector3d offset = new Vector3d(far).sub(farPoint.x, farPoint.y, farPoint.z);
        return rotation().conjugate().transform(offset).add(nearPoint.x, nearPoint.y, nearPoint.z);
    }

    public Vec3 dirToFar(Vec3 near) {
        Vector3d v = rotation().transform(new Vector3d(near.x, near.y, near.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 dirToNear(Vec3 far) {
        Vector3d v = rotation().conjugate().transform(new Vector3d(far.x, far.y, far.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vector3d dirToFar(Vector3dc near) {
        return rotation().transform(new Vector3d(near));
    }

    public Vector3d dirToNear(Vector3dc far) {
        return rotation().conjugate().transform(new Vector3d(far));
    }

    public Quaterniond orientToFar(Quaterniondc near) {
        return rotation().mul(near).normalize();
    }

    public Quaterniond orientToNear(Quaterniondc far) {
        return rotation().conjugate().mul(far).normalize();
    }

    public double depth(Vec3 nearPos) {
        return nearPos.subtract(nearPoint).dot(inward);
    }

    public double depth(Vector3dc nearPos) {
        return (nearPos.x() - nearPoint.x) * inward.x + (nearPos.y() - nearPoint.y) * inward.y
                + (nearPos.z() - nearPoint.z) * inward.z;
    }

    public Vec3 farInward() {
        return dirToFar(inward).reverse();
    }
}
