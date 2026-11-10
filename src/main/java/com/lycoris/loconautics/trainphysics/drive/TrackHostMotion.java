package com.lycoris.loconautics.trainphysics.drive;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class TrackHostMotion {

    private TrackHostMotion() {
    }

    public static Vector3d railVelocity(ServerLevel level, ServerSubLevel host, Vector3dc worldPoint, Vector3d dest) {
        if (host == null || level == null || worldPoint == null) {
            return dest.zero();
        }
        Vector3d plotPoint = host.logicalPose()
                .transformPositionInverse(new Vector3d(worldPoint), new Vector3d());
        return Sable.HELPER.getVelocity(level, host, plotPoint, dest);
    }

    public static double relativeAlongTrack(ServerLevel level, ServerSubLevel host, Vector3dc worldPoint,
                                     Vector3dc bodyVelocity, Vector3dc tangent) {
        Vector3d rail = railVelocity(level, host, worldPoint, new Vector3d());
        return new Vector3d(bodyVelocity).sub(rail).dot(tangent);
    }

    public static void pushHost(ServerSubLevel host, Vector3dc worldPoint, Vector3dc worldImpulse) {
        if (host == null || worldPoint == null) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null) {
            return;
        }
        Vector3d plotPoint = host.logicalPose()
                .transformPositionInverse(new Vector3d(worldPoint), new Vector3d());
        Vector3d localImpulse = new Quaterniond(host.logicalPose().orientation())
                .conjugate().transform(new Vector3d(worldImpulse).negate(), new Vector3d());
        handle.applyImpulseAtPoint(plotPoint, localImpulse);
    }
}
