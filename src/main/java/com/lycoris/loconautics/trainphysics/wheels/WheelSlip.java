package com.lycoris.loconautics.trainphysics.wheels;

import com.lycoris.loconautics.compat.computercraft.CcControl;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.signal.TrainSignalAccessor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;

public final class WheelSlip {

    private static final int STALL_DELAY_TICKS = 4;

    private static final int RELEASE_TICKS = 3;

    private static final double STALL_SPEED = 0.01;

    private static final double STALL_COMMAND = 0.02;

    private static final double SLIP_FRACTION = 0.05;

    private static final double SLIP_MAX_SPIN = 0.05;

    private static final double SLIP_MIN_GRIP = 0.3;

    private static final double SLIP_PULSE_RATE = 0.5;

    private WheelSlip() {
    }

    public static double carSpeed(SableTrain train, ServerSubLevel sub) {
        // in the rail hosts own frame, which for a sub level is plot coordinates and does not move when the host
        Vector3d now = inRailFrame(train, new Vector3d(sub.logicalPose().position()));
        Vector3d last = train.lastBodyPos();
        train.setLastBodyPos(now);
        if (last == null) {
            return train.speed();
        }
        double moved = Math.min(now.distance(last), Math.abs(train.speed()));
        return train.speed() < 0.0 ? -moved : moved;
    }

    private static Vector3d inRailFrame(SableTrain train, Vector3d worldPos) {
        SableTrain.Car car = train.car();
        ServerSubLevel host = car == null || car.carriage() == null ? null : car.carriage().activeSub();
        return host == null ? worldPos : host.logicalPose().transformPositionInverse(worldPos, worldPos);
    }

    public static double bite(long gameTime, BlockPos bogeyLocal) {
        double phase = gameTime * SLIP_PULSE_RATE + bogeyLocal.hashCode();
        return SLIP_MIN_GRIP + (1.0 - SLIP_MIN_GRIP) * Math.abs(Math.sin(phase));
    }

    public static boolean parked(SableTrain train) {
        return train.stationState() == SableTrain.StationState.STOPPED || TrainSignalAccessor.isHeldAtRed(train);
    }

    public static double surfaceSpeed(SableTrain train, ServerSubLevel sub, BlockPos bogeyLocal, double commanded,
                                      double carSpeed) {
        boolean computerBraking = CcControl.held(train.id()) && train.brakeScale() > 0.0;
        boolean locked = train.brakeHard() || train.brakeScale() >= 1.0 || computerBraking || parked(train)
                || ExternalForceDerail.staffHolds(sub);
        // commanded is the shafts rpm, a computer holding the train at 0 overrides the speed but never the shaft
        boolean computerIdle = CcControl.held(train.id()) && !CcControl.isDriving(train.id());
        boolean stuck = !locked && !computerIdle
                && Math.abs(commanded) > STALL_COMMAND && Math.abs(carSpeed) < STALL_SPEED;
        int was = train.wheelStall().getOrDefault(bogeyLocal, 0);
        int stall = locked || computerIdle ? 0
                : stuck ? Math.min(was + 1, STALL_DELAY_TICKS + RELEASE_TICKS) : Math.max(was - 1, 0);
        train.wheelStall().put(bogeyLocal, stall);
        boolean slipping = stall >= STALL_DELAY_TICKS || stall > 0 && train.slippingBogeys().contains(bogeyLocal);
        if (slipping) {
            train.slippingBogeys().add(bogeyLocal);
        } else {
            train.slippingBogeys().remove(bogeyLocal);
        }
        double w = slipping ? Math.copySign(Math.min(Math.abs(commanded) * SLIP_FRACTION, SLIP_MAX_SPIN), commanded)
                : carSpeed;
        train.wheelSurface().put(bogeyLocal, w);
        return w;
    }
}
