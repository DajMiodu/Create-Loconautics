package com.lycoris.loconautics.mixin.client.trainmap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.compat.trainmap.TrainMapSync.TrainMapSyncEntry;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.CreateClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

// one bad frame makes create switch the xaero map overlay off until restart, so a stale entry is skipped instead
@Mixin(value = TrainMapManager.class, remap = false)
public abstract class TrainMapSkipStaleEntryMixin {

    private static final Set<UUID> LOGGED = new HashSet<>();

    @WrapOperation(method = "drawTrains", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object loconautics$skipStaleEntry(Map<?, ?> map, Object key, Operation<Object> original) {
        Object entry = original.call(map, key);
        if (!(entry instanceof TrainMapSyncEntry sync) || !(key instanceof UUID id)) {
            return entry;
        }
        Train train = CreateClient.RAILWAYS.trains.get(id);
        if (train == null || sync.dimensions != null && sync.dimensions.size() >= train.carriages.size()) {
            LOGGED.remove(id);
            return entry;
        }
        if (LOGGED.add(id)) {
            LoconauticsConstants.LOGGER.info("[trainmap] skipped train {} on the map, client has {} carriages but the sync "
                    + "entry has {}", shortId(id), train.carriages.size(), sync.dimensions == null ? 0 : sync.dimensions.size());
        }
        return null;
    }
}
