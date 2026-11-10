package com.lycoris.loconautics.trainphysics.coupling;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.function.Consumer;

public interface CouplingProvider {

    void collectCoupledSubLevels(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours);

    default void collectWeldedSubLevels(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours) {
    }

    default void collectTwistLinks(ServerLevel level, UUID subLevelId, Consumer<TwistLink> links) {
    }
}
