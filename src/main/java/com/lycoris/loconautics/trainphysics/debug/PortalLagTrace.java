package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelOverlap;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class PortalLagTrace {

    private static final long LEVEL_SLOW_MS = 60L;
    private static final long TICK_SLOW_MS = 100L;
    private static final long LOG_GAP_TICKS = 20L;
    private static final long NANOS_PER_MS = 1_000_000L;
    private static final Map<ResourceKey<Level>, Long> LEVEL_START = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LEVEL_MS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LAST_LOGGED = new HashMap<>();
    private static long tickStart;

    private PortalLagTrace() {
    }

    @SubscribeEvent
    public static void onLevelPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEVEL_START.put(level.dimension(), System.nanoTime());
        }
    }

    @SubscribeEvent
    public static void onLevelPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Long start = LEVEL_START.get(level.dimension());
        if (start == null) {
            return;
        }
        long ms = (System.nanoTime() - start) / NANOS_PER_MS;
        LEVEL_MS.put(level.dimension(), ms);
        long now = level.getGameTime();
        if (ms >= LEVEL_SLOW_MS && now - LAST_LOGGED.getOrDefault(level.dimension(), -LOG_GAP_TICKS) >= LOG_GAP_TICKS) {
            LAST_LOGGED.put(level.dimension(), now);
            describe(level, ms);
        }
    }

    @SubscribeEvent
    public static void onTickPre(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void onTickPost(ServerTickEvent.Post event) {
        long ms = (System.nanoTime() - tickStart) / NANOS_PER_MS;
        if (ms >= TICK_SLOW_MS) {
            LoconauticsConstants.LOGGER.info("[portallag] tick took {} ms, levels={} {}", ms, LEVEL_MS,
                    PortalTwins.debugCounts());
        }
    }

    private static void describe(ServerLevel level, long ms) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        int bodies = 0;
        StringBuilder twins = new StringBuilder();
        for (ServerSubLevel sub : container.getAllSubLevels()) {
            bodies++;
            if (!PortalTwins.isTwin(sub)) {
                continue;
            }
            Vector3d pos = new Vector3d(sub.logicalPose().position());
            StringBuilder touching = new StringBuilder();
            for (ServerSubLevel other : container.getAllSubLevels()) {
                SubLevelOverlap.Hit hit = other == sub ? null : SubLevelOverlap.test(sub, other);
                if (hit != null) {
                    touching.append(' ').append(shortId(other)).append(String.format("(%.2f)", hit.depth()));
                }
            }
            twins.append(String.format("%n  twin %s at (%.1f %.1f %.1f) footprintLoaded=%s overlaps=[%s ]",
                    shortId(sub), pos.x, pos.y, pos.z, TrainResidency.footprintLoaded(level, pos), touching));
        }
        LoconauticsConstants.LOGGER.info("[portallag] {} level tick took {} ms, subLevels={} {}{}",
                level.dimension().location(), ms, bodies, PortalTwins.debugCounts(), twins);
    }
}
