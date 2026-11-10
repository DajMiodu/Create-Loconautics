package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class TurntableSignals {

    private static final Set<GlobalPos> RED = ConcurrentHashMap.newKeySet();

    private TurntableSignals() {
    }

    public static boolean forcedRed(Level level, BlockPos signal) {
        return level != null && !level.isClientSide && RED.contains(GlobalPos.of(level.dimension(), signal));
    }

    public static void hold(Level level, BlockPos signal, boolean red) {
        GlobalPos key = GlobalPos.of(level.dimension(), signal);
        boolean changed = red ? RED.add(key) : RED.remove(key);
        if (!changed || !level.isLoaded(signal) || !(level.getBlockEntity(signal) instanceof SignalBlockEntity ste)) {
            return;
        }
        SignalBoundary boundary = ste.getSignal();
        if (boundary != null) {
            boundary.updateBlockEntityPower(ste);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RED.clear();
    }
}
