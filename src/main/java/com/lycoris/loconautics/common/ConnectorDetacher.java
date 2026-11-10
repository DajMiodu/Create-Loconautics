package com.lycoris.loconautics.common;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

@FunctionalInterface
public interface ConnectorDetacher {

    int detachAll(ServerLevel level, UUID subLevelId);

    default boolean decoupleBetween(ServerLevel level, UUID subA, UUID subB) {
        return false;
    }
}
