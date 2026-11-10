package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;

public final class TrainSteeringAccessor {

    private TrainSteeringAccessor() {
    }

    public static void steer(SableTrain train, int dir) {
        if (train.car() == null) {
            return;
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
        BearingAxleBlockEntity axle = sub == null ? null : TrainPropulsion.bearingAxleIn(sub);
        Direction target = TrainSteering.target(TrainSteering.axleFront(axle, sub), dir);
        train.setSteerIntent(target);
        TrainSteering.clearFollow(train.car());
        TrainSteering.applySteerTarget(train.car(), target);
    }
}
