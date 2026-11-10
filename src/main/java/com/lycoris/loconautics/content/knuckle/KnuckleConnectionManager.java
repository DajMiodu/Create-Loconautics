package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KnuckleConnectionManager {

    private KnuckleConnectionManager() {
    }

    public static void watch(ServerSubLevelContainer container) {
        container.addObserver(new Watcher(container.getLevel()));
    }

    private record Free(KnuckleBlockEntity be, ServerSubLevel sub, Vector3d tip, Vector3d facing, boolean ready) {
    }

    private record Watch(KnuckleBlockEntity beA, ServerSubLevel subA, KnuckleBlockEntity beB, ServerSubLevel subB) {
    }

    private static final class Watcher implements SubLevelObserver {

        private final ServerLevel level;
        private final Set<UUID> removed = ConcurrentHashMap.newKeySet();
        private List<Watch> watched = new ArrayList<>();
        private int ticks;

        private Watcher(ServerLevel level) {
            this.level = level;
        }

        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            if (reason == SubLevelRemovalReason.REMOVED) {
                KnuckleJoint.releaseAllFor(subLevel.getUniqueId());
                KnuckleRailCoupler.releaseAllFor(subLevel.getUniqueId());
                if (!SableTeleportation.isInFlight(subLevel.getUniqueId())) {
                    removed.add(subLevel.getUniqueId());
                }
            }
        }

        @Override
        public void tick(SubLevelContainer subLevels) {
            if (!(subLevels instanceof ServerSubLevelContainer container)) {
                return;
            }
            if ((ticks++ % LoconauticsConfig.KNUCKLE_SCAN_INTERVAL.get()) != 0) {
                coupleWatched();
                return;
            }
            Map<UUID, List<KnuckleBlockEntity>> knuckles = collectKnuckles(container);
            dropRemovedPartners(container, knuckles);
            if (knuckles.isEmpty()) {
                return;
            }
            scanForNewConnections(container, knuckles);
            validateExistingConnections(container, knuckles);
        }

        private void dropRemovedPartners(ServerSubLevelContainer container, Map<UUID, List<KnuckleBlockEntity>> knuckles) {
            if (removed.isEmpty()) {
                return;
            }
            for (List<KnuckleBlockEntity> list : knuckles.values()) {
                for (KnuckleBlockEntity be : list) {
                    UUID partner = be.getPartnerSubLevelId();
                    boolean alive = partner != null && container.getSubLevel(partner) instanceof ServerSubLevel live
                            && !live.isRemoved();
                    if (partner != null && removed.contains(partner) && !alive) {
                        be.clearConnection();
                        be.clearPartner();
                    }
                }
            }
            removed.clear();
        }

        private void coupleWatched() {
            for (Watch w : watched) {
                if (w.subA().isRemoved() || w.subB().isRemoved() || w.beA().isRemoved() || w.beB().isRemoved()
                        || !available(w.beA()) || !available(w.beB())) {
                    continue;
                }
                if (canConnect(freeOf(w.beA(), w.subA()), freeOf(w.beB(), w.subB()))) {
                    connect(w.subA(), w.beA(), w.subB(), w.beB());
                }
            }
        }

        private void scanForNewConnections(ServerSubLevelContainer container,
                                           Map<UUID, List<KnuckleBlockEntity>> knuckles) {
            List<Free> free = new ArrayList<>();
            for (Map.Entry<UUID, List<KnuckleBlockEntity>> entry : knuckles.entrySet()) {
                ServerSubLevel sub = asSubLevel(container, entry.getKey());
                if (sub == null) {
                    continue;
                }
                for (KnuckleBlockEntity be : entry.getValue()) {
                    if (available(be)) {
                        free.add(freeOf(be, sub));
                    }
                }
            }
            List<Watch> near = new ArrayList<>();
            for (int i = 0; i < free.size(); i++) {
                Free a = free.get(i);
                for (int j = i + 1; j < free.size() && !a.be().isConnected(); j++) {
                    Free b = free.get(j);
                    if (b.be().isConnected()) {
                        continue;
                    }
                    if (canConnect(a, b)) {
                        connect(a.sub(), a.be(), b.sub(), b.be());
                    } else if (canMate(a, b, KnuckleTuning.WATCH_REACH)) {
                        near.add(new Watch(a.be(), a.sub(), b.be(), b.sub()));
                    }
                }
            }
            watched = near;
        }

        private boolean available(KnuckleBlockEntity be) {
            return !be.isConnected() && !be.isReleasing() && !be.isCouplingLocked() && !be.isRedstoneLocked()
                    && !stillHeldApart(be);
        }

        private boolean stillHeldApart(KnuckleBlockEntity be) {
            if (!be.isHeldApart()) {
                return false;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            ServerSubLevel own = Sable.HELPER.getContaining(be) instanceof ServerSubLevel s ? s : null;
            ServerSubLevel other = container == null ? null : asSubLevel(container, be.holdSubLevelId());
            if (own != null && other != null && be.holdLocalPos() != null
                    && other.getLevel().getBlockEntity(be.holdLocalPos()) instanceof KnuckleBlockEntity otherBe
                    && hingeGap(own, be, other, otherBe) < KnuckleTuning.mateOpenGap) {
                return true;
            }
            be.releaseHold();
            return false;
        }

        private Free freeOf(KnuckleBlockEntity be, ServerSubLevel sub) {
            Vector3d facing = worldFacing(sub, facing(be));
            return new Free(be, sub,
                    sub.logicalPose().transformPosition(KnuckleBlockEntity.tipLocal(be.getBlockPos(), facing(be))),
                    facing, matable(sub, facing));
        }

        private static boolean matable(ServerSubLevel sub, Vector3d facing) {
            SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
            if (train == null || train.isDerailed() || !KnuckleRailCoupler.glued(sub)) {
                return false;
            }
            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                if (train.isBogeyOffTrack(bogey.localPos())) {
                    return false;
                }
            }
            Vector3d up = sub.logicalPose().transformNormal(new Vector3d(0.0, 1.0, 0.0), new Vector3d()).normalize();
            return Math.abs(facing.y) <= KnuckleTuning.MAX_TILT_SIN
                    && Math.sqrt(up.x * up.x + up.z * up.z) <= KnuckleTuning.MAX_TILT_SIN;
        }

        private void validateExistingConnections(ServerSubLevelContainer container,
                                                 Map<UUID, List<KnuckleBlockEntity>> knuckles) {
            for (Map.Entry<UUID, List<KnuckleBlockEntity>> entry : knuckles.entrySet()) {
                ServerSubLevel sub = asSubLevel(container, entry.getKey());
                if (sub == null) {
                    continue;
                }
                for (KnuckleBlockEntity be : entry.getValue()) {
                    if (be.isConnected()) {
                        validateOne(container, sub, be);
                    } else if (be.isReleasing()) {
                        tickRelease(container, sub, be);
                    }
                }
            }
        }

        private void tickRelease(ServerSubLevelContainer container, ServerSubLevel sub, KnuckleBlockEntity be) {
            ServerSubLevel partnerSub = asSubLevel(container, be.getPartnerSubLevelId());
            BlockPos partnerPos = be.getPartnerLocalPos();
            if (partnerSub == null || partnerPos == null
                    || !(partnerSub.getLevel().getBlockEntity(partnerPos) instanceof KnuckleBlockEntity partnerBe)) {
                be.clearPartner();
                return;
            }
            if (canResume(sub, be, partnerBe)) {
                be.setConnection(partnerSub.getUniqueId(), partnerPos);
                partnerBe.setConnection(sub.getUniqueId(), be.getBlockPos());
                SubLevelContacts.disableCoupled(sub, partnerSub);
                return;
            }
            if (hingeGap(sub, be, partnerSub, partnerBe) >= KnuckleTuning.mateOpenGap) {
                be.clearPartner();
            }
        }

        private void validateOne(ServerSubLevelContainer container, ServerSubLevel sub, KnuckleBlockEntity be) {
            UUID partnerId = be.getPartnerSubLevelId();
            BlockPos partnerPos = be.getPartnerLocalPos();
            if (partnerId == null || partnerPos == null) {
                be.clearConnection();
                return;
            }
            SubLevel loaded = container.getSubLevel(partnerId);
            if (loaded == null) {
                return;
            }
            if (!(loaded instanceof ServerSubLevel partnerSub) || partnerSub.isRemoved()) {
                be.clearConnection();
                be.clearPartner();
                return;
            }
            if (!(partnerSub.getLevel().getBlockEntity(partnerPos) instanceof KnuckleBlockEntity partnerBe)
                    || !partnerBe.isConnected()
                    || !sub.getUniqueId().equals(partnerBe.getPartnerSubLevelId())
                    || !be.getBlockPos().equals(partnerBe.getPartnerLocalPos())) {
                if (be.partnerMissedTooLong()) {
                    be.clearConnection();
                    be.clearPartner();
                }
                return;
            }
            be.partnerSeen();
            SubLevelContacts.disableCoupled(sub, partnerSub);
            double gap = hingeGap(sub, be, partnerSub, partnerBe);
            if (be.isRedstoneLocked() || partnerBe.isRedstoneLocked() || tornApart(be, sub, partnerSub, gap)) {
                disconnect(level, be.getBlockPos());
                return;
            }
        }
    }

    private static void connect(ServerSubLevel subA, KnuckleBlockEntity beA, ServerSubLevel subB, KnuckleBlockEntity beB) {
        if (!KnuckleRailCoupler.glued(subA) || !KnuckleRailCoupler.glued(subB)) {
            return;
        }
        beA.setConnection(subB.getUniqueId(), beB.getBlockPos());
        beB.setConnection(subA.getUniqueId(), beA.getBlockPos());
        SubLevelContacts.disableCoupled(subA, subB);
    }

    private static boolean tornApart(KnuckleBlockEntity be, ServerSubLevel sub, ServerSubLevel partnerSub, double gap) {
        if (gap > KnuckleTuning.BREAK_DISTANCE) {
            return true;
        }
        return be.engageDone() && (!KnuckleRailCoupler.rigid(sub) || !KnuckleRailCoupler.rigid(partnerSub))
                && !ExternalForceDerail.staffHolds(sub) && !ExternalForceDerail.staffHolds(partnerSub);
    }

    private static boolean canMate(Free a, Free b, double reach) {
        if (a.sub() == b.sub() || !a.ready() || !b.ready()
                || a.facing().dot(b.facing()) > KnuckleTuning.OPPOSED_DOT) {
            return false;
        }
        Vector3d toB = b.tip().sub(a.tip(), new Vector3d());
        if (toB.length() >= reach) {
            return false;
        }
        return toB.dot(a.facing()) >= -KnuckleTuning.FRONT_SLACK && -toB.dot(b.facing()) >= -KnuckleTuning.FRONT_SLACK;
    }

    private static boolean canConnect(Free a, Free b) {
        if (!canMate(a, b, KnuckleTuning.connectDistance)) {
            return false;
        }
        Vector3d toB = b.tip().sub(a.tip(), new Vector3d());
        return toB.fma(-toB.dot(a.facing()), a.facing(), new Vector3d()).length() <= KnuckleTuning.MAX_OFFSET;
    }

    private static boolean canResume(ServerSubLevel sub, KnuckleBlockEntity be, KnuckleBlockEntity partnerBe) {
        return partnerBe.isReleasing()
                && sub.getUniqueId().equals(partnerBe.getPartnerSubLevelId())
                && be.getBlockPos().equals(partnerBe.getPartnerLocalPos())
                && !be.isRedstoneLocked() && !partnerBe.isRedstoneLocked()
                && !be.isCouplingLocked() && !partnerBe.isCouplingLocked();
    }

    private static double hingeGap(ServerSubLevel subA, KnuckleBlockEntity beA,
                                   ServerSubLevel subB, KnuckleBlockEntity beB) {
        Vector3d hingeA = subA.logicalPose().transformPosition(
                KnuckleBlockEntity.hingeLocal(beA.getBlockPos(), facing(beA)));
        Vector3d hingeB = subB.logicalPose().transformPosition(
                KnuckleBlockEntity.hingeLocal(beB.getBlockPos(), facing(beB)));
        return hingeA.distance(hingeB);
    }

    public static void disconnect(Level level, BlockPos worldPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(serverLevel.getBlockEntity(worldPos) instanceof KnuckleBlockEntity be) || !be.isConnected()) {
            return;
        }
        KnuckleBlockEntity partnerBe = loadedPartner(serverLevel, be);
        if (partnerBe != null && partnerBe.isConnected()) {
            partnerBe.clearConnection();
        }
        be.clearConnection();
    }

    public static void sever(Level level, BlockPos worldPos) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(serverLevel.getBlockEntity(worldPos) instanceof KnuckleBlockEntity be)) {
            return;
        }
        KnuckleBlockEntity partnerBe = loadedPartner(serverLevel, be);
        if (partnerBe != null && worldPos.equals(partnerBe.getPartnerLocalPos())) {
            partnerBe.clearConnection();
            partnerBe.clearPartner();
        }
    }

    private static KnuckleBlockEntity loadedPartner(ServerLevel level, KnuckleBlockEntity be) {
        UUID partnerSubLevelId = be.getPartnerSubLevelId();
        BlockPos partnerLocalPos = be.getPartnerLocalPos();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || partnerSubLevelId == null || partnerLocalPos == null
                || !(container.getSubLevel(partnerSubLevelId) instanceof ServerSubLevel partnerSub)) {
            return null;
        }
        return partnerSub.getLevel().getBlockEntity(partnerLocalPos) instanceof KnuckleBlockEntity partnerBe
                ? partnerBe : null;
    }

    private static Map<UUID, List<KnuckleBlockEntity>> collectKnuckles(ServerSubLevelContainer container) {
        Map<UUID, List<KnuckleBlockEntity>> cache = new HashMap<>();
        for (ServerSubLevel sub : container.getAllSubLevels()) {
            if (sub.isRemoved()) {
                continue;
            }
            List<KnuckleBlockEntity> list = SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class);
            if (!list.isEmpty()) {
                cache.put(sub.getUniqueId(), list);
            }
        }
        return cache;
    }

    private static ServerSubLevel asSubLevel(ServerSubLevelContainer container, UUID id) {
        if (id != null && container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
            return sub;
        }
        return null;
    }

    private static Direction facing(KnuckleBlockEntity be) {
        return be.getBlockState().getValue(KnuckleBlock.FACING);
    }

    private static Vector3d worldFacing(ServerSubLevel sub, Direction facing) {
        Vector3d dir = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return sub.logicalPose().transformNormal(dir, dir).normalize();
    }
}
