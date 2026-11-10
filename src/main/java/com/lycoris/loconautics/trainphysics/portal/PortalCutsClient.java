package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PortalCutsClient {

    private static final class Entry {
        private PortalCut cut;
        private BoundingBox3ic bounds;
        private final ClientLevel level;
        private final Set<BlockPos> hidden = new HashSet<>();

        private Entry(PortalCut cut, BoundingBox3ic bounds, ClientLevel level) {
            this.cut = cut;
            this.bounds = bounds;
            this.level = level;
        }
    }

    private static final List<Entry> ACTIVE = new CopyOnWriteArrayList<>();

    private PortalCutsClient() {
    }

    public static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    public static void clear() {
        for (Entry entry : new ArrayList<>(ACTIVE)) {
            restoreVisuals(entry);
        }
        ACTIVE.clear();
    }

    public static void onLevelUnload(ClientLevel level) {
        for (Entry entry : new ArrayList<>(ACTIVE)) {
            if (entry.level == level) {
                restoreVisuals(entry);
                ACTIVE.remove(entry);
            }
        }
    }

    public static void put(PortalCut cut) {
        ClientSubLevel sub = resolve(Minecraft.getInstance().level, cut.subLevelId());
        BoundingBox3ic bounds = sub == null ? null : sub.getPlot().getBoundingBox();
        if (bounds == null) {
            return;
        }
        for (Entry stale : new ArrayList<>(ACTIVE)) {
            if (stale.level != Minecraft.getInstance().level) {
                restoreVisuals(stale);
                ACTIVE.remove(stale);
            }
        }
        Entry existing = find(cut.subLevelId());
        if (existing != null && existing.cut.equals(cut) && bounds.equals(existing.bounds)) {
            return;
        }
        if (existing == null) {
            existing = new Entry(cut, bounds, Minecraft.getInstance().level);
            ACTIVE.add(existing);
        } else {
            existing.cut = cut;
            existing.bounds = bounds;
        }
        syncVisuals(sub, existing);
        remesh(sub);
    }

    public static void remove(UUID subLevelId) {
        Entry existing = find(subLevelId);
        if (existing == null) {
            return;
        }
        restoreVisuals(existing);
        ACTIVE.remove(existing);
        remesh(resolve(existing.level, subLevelId));
    }

    public static BlockState replacement(BlockPos pos, BlockState original) {
        for (Entry entry : ACTIVE) {
            if (entry.level != Minecraft.getInstance().level || !contains(entry.bounds, pos)) {
                continue;
            }
            if (entry.cut.hidden(pos.getX(), pos.getY(), pos.getZ())) {
                return Blocks.AIR.defaultBlockState();
            }
            if (!original.isAir() && entry.cut.seam(pos.getX(), pos.getY(), pos.getZ())) {
                return Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            }
            return null;
        }
        return null;
    }

    public static boolean hiddenAtPlot(double x, double y, double z) {
        if (ACTIVE.isEmpty()) {
            return false;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        for (Entry entry : ACTIVE) {
            if (entry.level == Minecraft.getInstance().level && contains(entry.bounds, pos)) {
                return entry.cut.hidden(pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return false;
    }

    public static boolean cutAtPlot(double x, double y, double z) {
        if (ACTIVE.isEmpty()) {
            return false;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        for (Entry entry : ACTIVE) {
            if (entry.level == Minecraft.getInstance().level && contains(entry.bounds, pos)) {
                return entry.cut.noCollider(pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return false;
    }

    public static boolean hiddenAtWorld(ClientSubLevel sub, double x, double y, double z) {
        if (ACTIVE.isEmpty()) {
            return false;
        }
        Entry entry = find(sub.getUniqueId());
        if (entry == null) {
            return false;
        }
        Vec3 local = sub.renderPose().transformPositionInverse(new Vec3(x, y, z));
        return entry.cut.hidden(Mth.floor(local.x), Mth.floor(local.y), Mth.floor(local.z));
    }

    public static boolean hiddenForBody(UUID subLevelId, double x, double y, double z) {
        if (ACTIVE.isEmpty() || subLevelId == null) {
            return false;
        }
        ClientSubLevel sub = resolve(Minecraft.getInstance().level, subLevelId);
        return sub != null && hiddenAtWorld(sub, x, y, z);
    }

    private static void syncVisuals(ClientSubLevel sub, Entry entry) {
        VisualizationManager manager = VisualizationManager.get(Minecraft.getInstance().level);
        if (manager == null) {
            return;
        }
        for (BlockEntity be : SubLevelBlocks.blockEntities(sub)) {
            BlockPos pos = be.getBlockPos();
            boolean gone = entry.cut.hidden(pos.getX(), pos.getY(), pos.getZ())
                    || entry.cut.seam(pos.getX(), pos.getY(), pos.getZ());
            if (gone && entry.hidden.add(pos.immutable())) {
                manager.blockEntities().queueRemove(be);
            } else if (!gone && entry.hidden.remove(pos)) {
                manager.blockEntities().queueAdd(be);
            }
        }
    }

    private static void restoreVisuals(Entry entry) {
        ClientSubLevel sub = resolve(entry.level, entry.cut.subLevelId());
        VisualizationManager manager = VisualizationManager.get(entry.level);
        if (sub == null || manager == null) {
            entry.hidden.clear();
            return;
        }
        for (BlockEntity be : SubLevelBlocks.blockEntities(sub)) {
            if (entry.hidden.contains(be.getBlockPos())) {
                manager.blockEntities().queueAdd(be);
            }
        }
        entry.hidden.clear();
    }

    private static boolean contains(BoundingBox3ic bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private static Entry find(UUID subLevelId) {
        for (Entry entry : ACTIVE) {
            if (entry.cut.subLevelId().equals(subLevelId)) {
                return entry;
            }
        }
        return null;
    }

    private static ClientSubLevel resolve(ClientLevel level, UUID subLevelId) {
        if (level == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.getSubLevel(subLevelId) instanceof ClientSubLevel sub ? sub : null;
    }

    private static void remesh(ClientSubLevel sub) {
        if (sub != null && sub.getRenderData() != null) {
            sub.getRenderData().rebuild();
        }
    }
}
