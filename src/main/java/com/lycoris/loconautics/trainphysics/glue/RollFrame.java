package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.drive.SeatBlend;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RollFrame {

    public final Vector3d reference;
    public final Vector3d tangent;
    public final Quaterniond seat;
    public final Vector3d up;
    public final Vector3d lateral;
    public final Vector3d forward0;
    public final Vector3d up0;
    public final Vector3d lateral0;
    public final Vector3d anchorLocal;
    public final Vector3d gLocal;
    public final double half;

    private RollFrame(Vector3d reference, Vector3d tangent, Quaterniond seat, Vector3d forward0, Vector3d up0,
                      Vector3d anchorLocal, double half) {
        this.reference = reference;
        this.tangent = tangent;
        this.seat = seat;
        this.forward0 = forward0;
        this.up0 = up0;
        this.up = seat.transform(new Vector3d(up0));
        this.lateral = new Vector3d(tangent).cross(up).normalize();
        this.lateral0 = new Vector3d(forward0).cross(up0).normalize();
        this.anchorLocal = anchorLocal;
        this.gLocal = new Vector3d(anchorLocal).fma(-CarriageRoll.SEAT_DROP, up0);
        this.half = half;
    }

    // null when the car has no glue anchor or no reference frame yet
    public static RollFrame of(ServerSubLevel sub, Car car, Vector3d[] ref) {
        RailCarriage carriage = car.carriage();
        Vector3d anchor = RailSnap.glueAnchorLocal(sub, car);
        if (carriage == null || anchor == null || ref == null) {
            return null;
        }
        Quaterniond seat = carriage.orientationTo(ref[1]);
        Vector3d forward0 = carriage.referenceForward();
        Vector3d up0 = carriage.referenceUp();
        if (forward0 == null || up0 == null) {
            return null;
        }
        return new RollFrame(new Vector3d(ref[0]), new Vector3d(ref[1]), seat, new Vector3d(forward0),
                new Vector3d(up0), anchor, RailGauge.halfGauge(carriage.trackMaterial()));
    }

    // side 0 is the center line, minus one and one are the left and right rail lines
    public Vector3d pointLocal(int side) {
        return new Vector3d(gLocal).fma(side * half, lateral0);
    }

    public PoseTweak.Applied seated(ServerSubLevel sub, SableTrain train, boolean decayBlend) {
        CarriageRoll.Roll roll = CarriageRoll.rollOf(sub);
        return leaned(sub, seatedLevel(sub, train, decayBlend), roll == null ? 0.0 : roll.suspension);
    }

    public PoseTweak.Applied seatedLevel(ServerSubLevel sub, SableTrain train, boolean decayBlend) {
        Quaterniond blended = SeatBlend.q(sub.getUniqueId(), seat, decayBlend);
        PoseTweak.Applied tweaked = PoseTweak.apply(sub, RailSnap.anchorTo(sub, anchorLocal, reference, blended),
                blended, forward0, train.poseTweak());
        return new PoseTweak.Applied(SeatBlend.pos(sub.getUniqueId(), tweaked.position()), tweaked.orientation());
    }

    // the body leans on its springs about the rail top center line, the wheels stay where they are
    public PoseTweak.Applied leaned(ServerSubLevel sub, PoseTweak.Applied level, double lean) {
        if (Math.abs(lean) < 1.0e-7) {
            return level;
        }
        Vector3d pivot = seatedPoint(sub, level, gLocal);
        Quaterniond turned = new Quaterniond(level.orientation()).rotateAxis(lean, forward0.x, forward0.y, forward0.z);
        Vector3d position = pivot.sub(turned.transform(new Vector3d(gLocal).sub(sub.logicalPose().rotationPoint())));
        return new PoseTweak.Applied(position, turned);
    }

    // a body point of the seated pose, in world space
    public static Vector3d seatedPoint(ServerSubLevel sub, PoseTweak.Applied seated, Vector3d local) {
        Vector3d offset = new Vector3d(local).sub(sub.logicalPose().rotationPoint());
        return seated.orientation().transform(offset).add(seated.position());
    }
}
