package com.lycoris.loconautics.trainphysics.door;

import com.lycoris.loconautics.trainphysics.SableTrain;

public final class TrainDoorsAccessor {

    private TrainDoorsAccessor() {
    }

    public static void setDoors(SableTrain train, boolean open) {
        TrainDoors.setDoors(train, open);
    }
}