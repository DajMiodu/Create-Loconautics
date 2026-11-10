package com.lycoris.loconautics.common;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ConnectorDetachers {

    private static final List<ConnectorDetacher> HANDLERS = new CopyOnWriteArrayList<>();

    private ConnectorDetachers() {
    }

    public static void register(ConnectorDetacher handler) {
        if (handler != null) {
            HANDLERS.add(handler);
        }
    }

    // one handler throwing cant strand the rest still coupled
    public static int detachAll(ServerLevel level, UUID subLevelId) {
        if (subLevelId == null) {
            return 0;
        }
        int released = 0;
        for (ConnectorDetacher handler : HANDLERS) {
            try {
                released += handler.detachAll(level, subLevelId);
            } catch (RuntimeException failure) {
                LoconauticsConstants.LOGGER.error("[trainlookup] detach handler failed for sub level {}",
                        subLevelId, failure);
            }
        }
        return released;
    }

    public static boolean decoupleBetween(ServerLevel level, UUID subA, UUID subB) {
        boolean split = false;
        for (ConnectorDetacher handler : HANDLERS) {
            try {
                split |= handler.decoupleBetween(level, subA, subB);
            } catch (RuntimeException failure) {
                LoconauticsConstants.LOGGER.error("[maneuver] decouple handler failed for {} and {}", subA, subB, failure);
            }
        }
        return split;
    }
}
