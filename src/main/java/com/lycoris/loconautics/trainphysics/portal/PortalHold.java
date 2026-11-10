package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortalHold {

    private static final SubLevelLoadingTicketType<Unit> HELD_AT_PORTAL =
            SubLevelLoadingTicketType.create(LoconauticsConstants.id("portal_entry"), Unit.CODEC);

    private static final int FAR_TICKET_RADIUS = 3;

    private static final int FAR_READY_RADIUS = 2;

    private static final Map<UUID, ResourceKey<Level>> HELD = new HashMap<>();

    private PortalHold() {
    }

    public static void hold(ServerLevel level, UUID subLevelId) {
        if (HELD.containsKey(subLevelId)) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        if (container == null || sub == null || sub.isRemoved()) {
            return;
        }
        container.addForceLoadTicket(sub, HELD_AT_PORTAL, Unit.INSTANCE);
        HELD.put(subLevelId, level.dimension());
    }

    // true once the far chunks around the car are loaded, the ticket keeps them loaded so a twin clone never blocks on a cold chunk
    public static boolean holdFarChunks(ServerLevel farLevel, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        ChunkPos chunk = new ChunkPos(pos);
        farLevel.getChunkSource().addRegionTicket(TicketType.PORTAL, chunk, FAR_TICKET_RADIUS, pos);
        for (int dx = -FAR_READY_RADIUS; dx <= FAR_READY_RADIUS; dx++) {
            for (int dz = -FAR_READY_RADIUS; dz <= FAR_READY_RADIUS; dz++) {
                if (farLevel.getChunkSource().getChunkNow(chunk.x + dx, chunk.z + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void release(MinecraftServer server, UUID subLevelId) {
        ResourceKey<Level> dim = HELD.remove(subLevelId);
        if (dim == null || server == null) {
            return;
        }
        ServerLevel level = server.getLevel(dim);
        ServerSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        ServerSubLevel sub = level == null ? null : SubLevelData.resolve(level, subLevelId);
        if (container != null && sub != null && !sub.isRemoved()) {
            container.removeForceLoadTicket(sub, HELD_AT_PORTAL, Unit.INSTANCE);
        }
    }

    public static void releaseAll(MinecraftServer server) {
        for (UUID subLevelId : new ArrayList<>(HELD.keySet())) {
            release(server, subLevelId);
        }
    }

    public static void sweep(MinecraftServer server, Set<UUID> stillEntering) {
        for (UUID subLevelId : new ArrayList<>(HELD.keySet())) {
            if (!stillEntering.contains(subLevelId)) {
                release(server, subLevelId);
            }
        }
    }
}
