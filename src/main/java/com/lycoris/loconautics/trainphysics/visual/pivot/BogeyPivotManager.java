package com.lycoris.loconautics.trainphysics.visual.pivot;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class BogeyPivotManager {

    private record PivotKey(UUID subLevel, BlockPos local) {
    }

    private static final Map<PivotKey, BogeyPivotBody> BODIES = new HashMap<>();


    private BogeyPivotManager() {
    }

    public static void clear() {
        BODIES.clear();
    }

    public static void register() {
        SableEventPlatform.INSTANCE.onPhysicsTick(BogeyPivotManager::onPrePhysicsTick);
    }

    static void onPrePhysicsTick(SubLevelPhysicsSystem system, double dt) {
        if (!LoconauticsConfig.BOGEY_PHYSICS_PIVOT.get()) {
            return;
        }
        ServerLevel level = system.getLevel();
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() != level || train.isDerailed() || train.isPhysics() || train.car() == null) {
                continue;
            }
            UUID subId = train.car().subLevelId();
            if (subId == null) {
                continue;
            }
            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                PivotKey key = new PivotKey(subId, bogey.localPos());
                BogeyPivotBody body = BODIES.computeIfAbsent(key, k -> new BogeyPivotBody());
                Quaterniond neutral = bogey.rail().leadingTangentQuaternion();
                Quaterniond target = new Quaterniond(neutral);
                body.drive(dt, target);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BODIES.isEmpty()) {
            return;
        }
        boolean enabled = LoconauticsConfig.BOGEY_PHYSICS_PIVOT.get();
        Set<PivotKey> live = new HashSet<>();

        if (enabled) {
            for (SableTrain train : SableTrainRegistry.all()) {
                if (train.isDerailed() || train.isPhysics()) {
                    continue;
                }
                ServerSubLevel sub = resolveSub(train);
                if (sub == null) {
                    continue;
                }
                UUID subId = train.car().subLevelId();
                ServerLevel level = sub.getLevel();
                for (SableTrain.Bogey bogey : train.car().bogeys()) {
                    PivotKey key = new PivotKey(subId, bogey.localPos());
                    live.add(key);
                    BogeyPivotBody body = BODIES.get(key);
                    if (body == null) {
                        continue;
                    }
                }
            }
        }

        Iterator<Map.Entry<PivotKey, BogeyPivotBody>> it = BODIES.entrySet().iterator();
        while (it.hasNext()) {
            PivotKey key = it.next().getKey();
            if (!enabled || !live.contains(key)) {
                it.remove();
            }
        }
    }

    private static ServerSubLevel resolveSub(SableTrain train) {
        UUID subId = train.car() == null ? null : train.car().subLevelId();
        if (subId == null) {
            return null;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return null;
        }
        return container.getSubLevel(subId) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
    }
}
