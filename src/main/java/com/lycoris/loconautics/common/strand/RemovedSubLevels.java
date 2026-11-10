package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.common.sable.SableTeleportation;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RemovedSubLevels {

    private static final Set<UUID> REMOVED = ConcurrentHashMap.newKeySet();

    private RemovedSubLevels() {
    }

    public static void watch(ServerSubLevelContainer container) {
        container.addObserver(new SubLevelObserver() {
            @Override
            public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                if (reason == SubLevelRemovalReason.REMOVED && !SableTeleportation.isInFlight(subLevel.getUniqueId())) {
                    REMOVED.add(subLevel.getUniqueId());
                }
            }
        });
    }

    public static boolean contains(@Nullable UUID subLevelId) {
        return subLevelId != null && REMOVED.contains(subLevelId);
    }
}
