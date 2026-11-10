package com.lycoris.loconautics.trainphysics.teleport;

import com.lycoris.loconautics.content.hose.HoseStrand;
import com.lycoris.loconautics.content.hose.HoseStrandManager;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.StrandEndpoints;
import com.lycoris.loconautics.common.strand.StrandLength;
import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandDriver;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrand;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrandManager;
import com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRopeManager;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import it.unimi.dsi.fastutil.objects.ObjectList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeleportStrands {

    public record Shift(BlockPos from, BlockPos to) {

        public BlockPos apply(BlockPos pos) {
            return new BlockPos(pos.getX() - from.getX() + to.getX(), pos.getY(), pos.getZ() - from.getZ() + to.getZ());
        }

        public Vector3d apply(Vector3d local) {
            return local.add(to.getX() - from.getX(), 0.0, to.getZ() - from.getZ());
        }
    }

    private record HoseJoint(HoseStrand strand, @Nullable UUID winchSub) {
    }

    private record RopeShape(List<Vector3d> viaA, List<Vector3d> viaB, double extension) {

        static RopeShape of(@Nullable SubLevelContainer container, ServerRopeStrand strand, RopeAttachment start,
                            RopeAttachment end) {
            ServerSubLevel a = StrandEndpoints.subLevel(container, start.subLevelID());
            ServerSubLevel b = StrandEndpoints.subLevel(container, end.subLevelID());
            if (a == null || b == null) {
                return null;
            }
            if (strand.isActive()) {
                strand.updatePose();
            }
            List<Vector3d> viaA = new ArrayList<>();
            List<Vector3d> viaB = new ArrayList<>();
            for (Vector3d point : strand.getPoints()) {
                viaA.add(a.logicalPose().transformPositionInverse(point, new Vector3d()));
                viaB.add(b.logicalPose().transformPositionInverse(point, new Vector3d()));
            }
            return new RopeShape(viaA, viaB, strand.getExtension());
        }
    }

    private record RopeJoint(UUID subA, BlockPos localA, UUID subB, BlockPos localB, @Nullable RopeShape shape) {
    }

    public static final class Carry {
        private final List<RopeJoint> ropes = new ArrayList<>();
        private final List<SteelCableStrand> cables = new ArrayList<>();
        private final List<ScrewLinkStrand> screws = new ArrayList<>();
        private final List<HoseJoint> hoses = new ArrayList<>();
        private int cut;

        public int cut() {
            return cut;
        }

        public int carried() {
            return ropes.size() + cables.size() + screws.size() + hoses.size();
        }
    }

    @Nullable
    private static Field pointsField;

    private static final List<RopeJoint> PENDING = new ArrayList<>();

    private TeleportStrands() {
    }

    public static Carry capture(ServerLevel level, Set<UUID> moving) {
        Carry carry = new Carry();
        captureRopes(level, moving, carry);
        captureCables(level, moving, carry);
        captureScrews(level, moving, carry);
        captureHoses(level, moving, carry);
        return carry;
    }

    private static void captureRopes(ServerLevel level, Set<UUID> moving, Carry carry) {
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (manager == null) {
            return;
        }
        Map<UUID, ServerRopeStrand> found = new LinkedHashMap<>();
        for (ServerRopeStrand strand : manager.getAllStrands()) {
            found.put(strand.getUUID(), strand);
        }
        for (UUID id : moving) {
            ServerSubLevel body = StrandEndpoints.subLevel(container, id);
            for (BlockEntity be : SubLevelBlocks.blockEntities(body)) {
                if (be instanceof SmartBlockEntity smart
                        && smart.getBehaviour(RopeStrandHolderBehavior.TYPE) instanceof RopeStrandHolderBehavior holder) {
                    if (holder.ownsRope() && holder.getOwnedStrand() != null) {
                        found.putIfAbsent(holder.getOwnedStrand().getUUID(), holder.getOwnedStrand());
                    }
                    ServerLevelRopeManager holderManager = ServerLevelRopeManager.getOrCreate(be.getLevel());
                    if (holderManager != null) {
                        for (ServerRopeStrand strand : holderManager.getAllStrands()) {
                            found.putIfAbsent(strand.getUUID(), strand);
                        }
                    }
                }
            }
        }
        for (ServerRopeStrand strand : List.copyOf(found.values())) {
            RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
            RopeAttachment end = strand.getAttachment(RopeAttachmentPoint.END);
            boolean startMoves = start != null && moving.contains(start.subLevelID());
            boolean endMoves = end != null && moving.contains(end.subLevelID());
            if (!startMoves && !endMoves) {
                continue;
            }
            boolean carried = startMoves && endMoves;
            boolean held = !carried && start != null && end != null && start.subLevelID() != null
                    && end.subLevelID() != null;
            RopeShape shape = carried || held ? RopeShape.of(container, strand, start, end) : null;
            if (!destroyRope(level, container, manager, strand, start, !carried && !held)) {
                continue;
            }
            if (carried || held) {
                RopeJoint joint = new RopeJoint(start.subLevelID(), start.blockAttachment(),
                        end.subLevelID(), end.blockAttachment(), shape);
                (carried ? carry.ropes : PENDING).add(joint);
            } else {
                carry.cut++;
            }
        }
    }

    private static boolean destroyRope(ServerLevel level, @Nullable SubLevelContainer container,
                                       ServerLevelRopeManager manager, ServerRopeStrand strand,
                                       @Nullable RopeAttachment start, boolean dropItem) {
        RopeStrandHolderBehavior owner = start == null ? null
                : holderAt(level, container, start.subLevelID(), start.blockAttachment());
        if (owner != null && owner.getOwnedStrand() == strand) {
            owner.destroyRope(null, null, dropItem);
            return true;
        }
        LoconauticsConstants.LOGGER.warn("[teleport] rope {} has no live owner, removing it without its holder",
                strand.getUUID());
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        if (system != null && strand.isActive()) {
            system.removeObject(strand);
        }
        manager.removeStrand(strand.getUUID());
        return false;
    }

    private static void captureCables(ServerLevel level, Set<UUID> moving, Carry carry) {
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        SteelCableRopeManager ropes = SteelCableRopeManager.get(level);
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        if (strands == null || ropes == null || system == null) {
            return;
        }
        for (SteelCableStrand strand : List.copyOf(strands.all())) {
            boolean aMoves = moving.contains(strand.subA);
            boolean bMoves = moving.contains(strand.subB);
            if (!aMoves && !bMoves) {
                continue;
            }
            if (aMoves && bMoves) {
                ropes.remove(system, strand.id);
                PacketDistributor.sendToPlayersInDimension(level,
                        StrandPointsPacket.removal(strand.type.kind(), strand.id));
                carry.cables.add(strand);
            } else {
                SteelCableStrandDriver.detach(level, strands, ropes, strand, null);
                carry.cut++;
            }
        }
    }

    private static void captureScrews(ServerLevel level, Set<UUID> moving, Carry carry) {
        ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(level);
        ScrewLinkRopeManager ropes = ScrewLinkRopeManager.get(level);
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        if (strands == null || ropes == null || system == null) {
            return;
        }
        for (ScrewLinkStrand strand : List.copyOf(strands.all())) {
            boolean aMoves = moving.contains(strand.subA);
            boolean bMoves = strand.isCoupled() && moving.contains(strand.subB);
            if (!aMoves && !bMoves) {
                continue;
            }
            ropes.remove(system, strand.id);
            PacketDistributor.sendToPlayersInDimension(level,
                    StrandPointsPacket.removal(StrandKind.SCREW_LINK, strand.id));
            if (strand.isCoupled() && aMoves != bMoves) {
                strand.subB = null;
                strand.localB = null;
                carry.cut++;
            }
            if (aMoves) {
                carry.screws.add(strand);
            } else {
                strand.points = null;
                strands.touch();
            }
        }
    }

    private static void captureHoses(ServerLevel level, Set<UUID> moving, Carry carry) {
        HoseStrandManager strands = HoseStrandManager.get(level);
        HoseRopeManager ropes = HoseRopeManager.get(level);
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (strands == null || ropes == null || system == null) {
            return;
        }
        for (HoseStrand strand : List.copyOf(strands.all())) {
            SubLevel winchBody = Sable.HELPER.getContaining(level, strand.winchPos);
            UUID winchId = winchBody == null ? null : winchBody.getUniqueId();
            boolean winchMoves = winchId != null && moving.contains(winchId);
            boolean nozzleMoves = strand.nozzleSubLevelId != null && moving.contains(strand.nozzleSubLevelId);
            if (!winchMoves && !nozzleMoves) {
                continue;
            }
            if (!winchMoves || strand.nozzleLocalPos != null && !nozzleMoves) {
                if (StrandEndpoints.blockEntity(level, container, winchId, strand.winchPos) instanceof HoseWinchBlockEntity winch) {
                    winch.onWinchBroken();
                }
                carry.cut++;
                continue;
            }
            ropes.remove(system, strand.id);
            PacketDistributor.sendToPlayersInDimension(level, StrandPointsPacket.removal(StrandKind.HOSE_LINK, strand.id));
            carry.hoses.add(new HoseJoint(strand, winchId));
        }
    }

    public static void restore(Carry carry, Map<UUID, ServerLevel> where, ServerLevel fallback, Map<UUID, Shift> shifts) {
        for (RopeJoint joint : carry.ropes) {
            respawnRope(joint, where, fallback, shifts);
        }
        for (SteelCableStrand strand : carry.cables) {
            respawnCable(strand, where, fallback, shifts);
        }
        for (ScrewLinkStrand strand : carry.screws) {
            respawnScrew(strand, where, fallback, shifts);
        }
        for (HoseJoint joint : carry.hoses) {
            respawnHose(joint, where, fallback, shifts);
        }
        settlePending(fallback.getServer(), shifts);
    }

    // a rope between two cars that cross a portal one after the other waits here until both are in the same level
    private static void settlePending(MinecraftServer server, Map<UUID, Shift> shifts) {
        for (RopeJoint joint : List.copyOf(PENDING)) {
            PENDING.remove(joint);
            RopeJoint moved = shiftJoint(joint, shifts);
            ServerLevel a = levelOf(server, moved.subA());
            ServerLevel b = levelOf(server, moved.subB());
            if (a == null || b == null) {
                LoconauticsConstants.LOGGER.warn("[teleport] held rope between {} and {} lost a body, dropping it",
                        moved.subA(), moved.subB());
                continue;
            }
            if (a != b) {
                PENDING.add(moved);
                continue;
            }
            respawnRope(moved, Map.of(), a, Map.of());
        }
    }

    private static RopeJoint shiftJoint(RopeJoint joint, Map<UUID, Shift> shifts) {
        RopeShape shape = joint.shape();
        RopeShape shifted = shape == null ? null : new RopeShape(
                shape.viaA().stream().map(point -> shiftLocal(shifts, joint.subA(), point)).toList(),
                shape.viaB().stream().map(point -> shiftLocal(shifts, joint.subB(), point)).toList(),
                shape.extension());
        return new RopeJoint(joint.subA(), shifted(shifts, joint.subA(), joint.localA()), joint.subB(),
                shifted(shifts, joint.subB(), joint.localB()), shifted);
    }

    @Nullable
    private static ServerLevel levelOf(MinecraftServer server, UUID sub) {
        for (ServerLevel candidate : server.getAllLevels()) {
            if (StrandEndpoints.subLevel(SubLevelContainer.getContainer(candidate), sub) != null) {
                return candidate;
            }
        }
        return null;
    }

    private static void respawnHose(HoseJoint joint, Map<UUID, ServerLevel> where, ServerLevel fallback,
                                    Map<UUID, Shift> shifts) {
        HoseStrand strand = joint.strand();
        ServerLevel level = where.getOrDefault(joint.winchSub(), fallback);
        HoseStrandManager strands = HoseStrandManager.get(level);
        if (strands == null) {
            return;
        }
        strand.winchPos = shifted(shifts, joint.winchSub(), strand.winchPos);
        if (strand.nozzleLocalPos != null) {
            strand.nozzleLocalPos = shifted(shifts, strand.nozzleSubLevelId, strand.nozzleLocalPos);
        }
        strand.points = null;
        strands.touch();
        LoconauticsConstants.LOGGER.info("[strandcross] hose {} rebuilt in {} nozzle={}", strand.id,
                level.dimension().location(), strand.nozzleLocalPos != null);
    }

    private static void respawnScrew(ScrewLinkStrand strand, Map<UUID, ServerLevel> where, ServerLevel fallback,
                                     Map<UUID, Shift> shifts) {
        ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(where.getOrDefault(strand.subA, fallback));
        if (strands == null) {
            return;
        }
        strand.localA = shifted(shifts, strand.subA, strand.localA);
        if (strand.isCoupled()) {
            strand.localB = shifted(shifts, strand.subB, strand.localB);
        }
        strand.points = null;
        strands.touch();
        LoconauticsConstants.LOGGER.info("[strandcross] screw link {} rebuilt in {} coupled={}", strand.id,
                where.getOrDefault(strand.subA, fallback).dimension().location(), strand.isCoupled());
    }

    private static BlockPos shifted(Map<UUID, Shift> shifts, UUID sub, BlockPos local) {
        Shift shift = shifts.get(sub);
        return shift == null ? local : shift.apply(local);
    }

    private static void respawnRope(RopeJoint joint, Map<UUID, ServerLevel> where, ServerLevel fallback,
                                    Map<UUID, Shift> shifts) {
        ServerLevel level = where.getOrDefault(joint.subA(), fallback);
        BlockPos localA = shifted(shifts, joint.subA(), joint.localA());
        BlockPos localB = shifted(shifts, joint.subB(), joint.localB());
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        RopeStrandHolderBehavior from = holderAt(level, container, joint.subA(), localA);
        RopeStrandHolderBehavior to = where.getOrDefault(joint.subB(), fallback) == level
                ? holderAt(level, container, joint.subB(), localB) : null;
        boolean made = from != null && to != null && from.createRope(to, false);
        LoconauticsConstants.LOGGER.info("[strandcross] rope {} to {} holders={}/{} made={} in {}", joint.subA(),
                joint.subB(), from != null, to != null, made, level.dimension().location());
        if (made) {
            restoreShape(from, joint, where, fallback, shifts);
            return;
        }
        Vector3d at = StrandEndpoints.worldPoint(level, container, joint.subA(), localA);
        LoconauticsConstants.LOGGER.warn("[teleport] rope between {} and {} could not be respawned, dropping it",
                joint.subA(), joint.subB());
        drop(level, at, new ItemStack(SimItems.ROPE_COUPLING.get()));
    }

    // createrope lays a straight line between the holders, so the old sag is put back before physics adds the strand
    private static void restoreShape(RopeStrandHolderBehavior from, RopeJoint joint, Map<UUID, ServerLevel> where,
                                     ServerLevel fallback, Map<UUID, Shift> shifts) {
        RopeShape shape = joint.shape();
        ServerRopeStrand strand = from.getOwnedStrand();
        Field field = pointsField();
        if (shape == null || strand == null || field == null || strand.isActive()) {
            return;
        }
        ServerSubLevel a = StrandEndpoints.subLevel(SubLevelContainer.getContainer(where.getOrDefault(joint.subA(), fallback)),
                joint.subA());
        ServerSubLevel b = StrandEndpoints.subLevel(SubLevelContainer.getContainer(where.getOrDefault(joint.subB(), fallback)),
                joint.subB());
        if (a == null || b == null) {
            return;
        }
        int count = shape.viaA().size();
        List<Vector3d> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vector3d atA = a.logicalPose().transformPosition(shiftLocal(shifts, joint.subA(), shape.viaA().get(i)),
                    new Vector3d());
            Vector3d atB = b.logicalPose().transformPosition(shiftLocal(shifts, joint.subB(), shape.viaB().get(i)),
                    new Vector3d());
            points.add(atA.lerp(atB, count < 2 ? 0.0 : i / (double) (count - 1)));
        }
        try {
            @SuppressWarnings("unchecked")
            ObjectList<Vector3d> live = (ObjectList<Vector3d>) field.get(strand);
            live.clear();
            live.addAll(points);
        } catch (IllegalAccessException e) {
            LoconauticsConstants.LOGGER.warn("[teleport] could not put the rope shape back, it stays straight", e);
            return;
        }
        strand.updateFirstSegmentExtension(shape.extension());
    }

    private static Vector3d shiftLocal(Map<UUID, Shift> shifts, UUID sub, Vector3d local) {
        Shift shift = shifts.get(sub);
        Vector3d copy = new Vector3d(local);
        return shift == null ? copy : shift.apply(copy);
    }

    @Nullable
    private static Field pointsField() {
        if (pointsField == null) {
            try {
                Field field = RopePhysicsObject.class.getDeclaredField("points");
                field.setAccessible(true);
                pointsField = field;
            } catch (NoSuchFieldException | RuntimeException e) {
                LoconauticsConstants.LOGGER.warn("[teleport] rope points field is not reachable", e);
            }
        }
        return pointsField;
    }

    private static void respawnCable(SteelCableStrand strand, Map<UUID, ServerLevel> where, ServerLevel fallback,
                                     Map<UUID, Shift> shifts) {
        ServerLevel level = where.getOrDefault(strand.subA, fallback);
        strand.localA = shifted(shifts, strand.subA, strand.localA);
        strand.localB = shifted(shifts, strand.subB, strand.localB);
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        SteelCableRopeManager ropes = SteelCableRopeManager.get(level);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (strands == null || ropes == null) {
            return;
        }
        Vector3d a = StrandEndpoints.worldPoint(level, container, strand.subA, strand.localA);
        Vector3d b = StrandEndpoints.worldPoint(level, container, strand.subB, strand.localB);
        boolean sameLevel = where.getOrDefault(strand.subB, fallback) == level;
        double length = a == null || b == null || !sameLevel ? StrandLength.TOO_LONG
                : StrandLength.spawnLength(a, b, strand.type.maxSpan());
        if (length < 0.0) {
            LoconauticsConstants.LOGGER.warn("[teleport] strand {} does not fit between its ends any more, dropping it",
                    strand.id);
            SteelCableStrandDriver.detach(level, strands, ropes, strand, null);
            return;
        }
        LoconauticsConstants.LOGGER.info("[strandcross] cable {} rebuilt in {} length={}", strand.id,
                level.dimension().location(), length);
        strand.restLength = length;
        strand.points = null;
        strand.extensionGoal = 1.0;
        strands.touch();
    }

    @Nullable
    private static RopeStrandHolderBehavior holderAt(ServerLevel level, @Nullable SubLevelContainer container,
                                                     @Nullable UUID sub, BlockPos local) {
        BlockEntity be = StrandEndpoints.blockEntity(level, container, sub, local);
        return be instanceof SmartBlockEntity smart ? smart.getBehaviour(RopeStrandHolderBehavior.TYPE) : null;
    }

    private static void drop(ServerLevel level, @Nullable Vector3d at, ItemStack stack) {
        if (at != null) {
            Containers.dropItemStack(level, at.x, at.y, at.z, stack);
        }
    }
}
