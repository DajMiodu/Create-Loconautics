package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.StationState;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;

public final class ConsistBrake {

    public record Demand(double decel, boolean hold) {}

    public static final Demand NONE = new Demand(0.0, false);

    private ConsistBrake() {
    }

    public static Demand demandFor(SableTrain train) {
        ServerLevel level = train.level();
        UUID myId = train.car() == null ? null : train.car().subLevelId();
        if (level == null || myId == null) {
            return NONE;
        }
        Set<UUID> consist = TrainConsist.connectedSubLevels(level, myId);
        if (consist.size() <= 1) {
            return NONE;
        }
        double strongest = 0.0;
        boolean hold = false;
        for (UUID id : consist) {
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other == null) {
                continue;
            }
            if (pulling(other)) {
                return NONE;
            }
            if (other.stationState() == StationState.STOPPED || other.scheduleHold()) {
                hold = true;
                strongest = TrainBrakes.BRAKE_DECEL;
            }
        }
        return strongest <= 0.0 && !hold ? NONE : new Demand(strongest, hold);
    }

    private static boolean pulling(SableTrain train) {
        if (train.stationState() == StationState.DEPARTING
                || com.lycoris.loconautics.compat.computercraft.CcControl.isDriving(train.id())) {
            return true;
        }
        AnalogControllerBlockEntity drive = SableTrainDriver.controllerIn(train);
        if (drive == null) {
            return false;
        }
        return drive.isForwardHeld() || drive.isReverseHeld()
                || (drive.isScheduleControlled() && drive.getCurrentPower() != 0);
    }
}
