package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ConsistFollow {

    private ConsistFollow() {
    }

    public static void apply(SableTrain train) {
        if (train.servoBrake() || train.brakeHold() || train.isDerailed() || train.isPhysics()
                || train.car() == null || train.car().subLevelId() == null) {
            return;
        }
        ServerLevel level = train.level();
        UUID myId = train.car().subLevelId();
        if (level == null) {
            return;
        }
        SableTrain leader = null;
        for (UUID id : TrainConsist.connectedSubLevels(level, myId)) {
            SableTrain other = id.equals(myId) ? null : SableTrainRegistry.bySubLevel(id);
            if (other != null && other.servoBrake() && !other.isFollowing() && !other.isDerailed()
                    && other.car() != null && other.level() == level) {
                leader = other;
                break;
            }
        }
        if (leader == null) {
            return;
        }
        Vec3 mine = SableTrainDriver.scheduleDriveTangent(train);
        Vec3 theirs = SableTrainDriver.scheduleDriveTangent(leader);
        double sense = mine == null || theirs == null || mine.dot(theirs) >= 0.0 ? 1.0 : -1.0;
        train.setTargetSpeed(leader.targetSpeed() * sense);
        train.setServoBrake(true);
        train.setFollowing(true);
    }
}
