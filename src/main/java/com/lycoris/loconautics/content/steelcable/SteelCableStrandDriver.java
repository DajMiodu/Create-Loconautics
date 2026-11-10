package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.RemovedSubLevels;
import com.lycoris.loconautics.common.strand.StrandEndpoints;
import com.lycoris.loconautics.common.strand.StrandSnap;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRope;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class SteelCableStrandDriver {

    private enum EndpointState { OK, NOT_LOADED, GONE }

    private SteelCableStrandDriver() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        SteelCableStrandManager strands = SteelCableStrandManager.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            SteelCableRopeManager ropes = SteelCableRopeManager.get(level);
            SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (ropes == null || system == null || container == null) {
                continue;
            }
            for (SteelCableStrand strand : List.copyOf(strands.all())) {
                if (StrandEndpoints.drivenBy(level, strand.subA, strand.subB)) {
                    driveStrand(level, container, system, strands, ropes, strand);
                }
            }
        }
    }

    private static void driveStrand(ServerLevel level, @Nullable SubLevelContainer container,
                                     SubLevelPhysicsSystem system, SteelCableStrandManager strands,
                                     SteelCableRopeManager ropes, SteelCableStrand strand) {
        EndpointState stateA = resolveEndpoint(level, container, strand.subA, strand.localA, strand);
        EndpointState stateB = resolveEndpoint(level, container, strand.subB, strand.localB, strand);
        if (stateA == EndpointState.GONE || stateB == EndpointState.GONE) {
            Vector3d at = snapPoint(level, container, strand, stateA == EndpointState.GONE);
            if (at == null) {
                detach(level, strands, ropes, strand, null);
                return;
            }
            snap(level, strands, ropes, strand, at);
            return;
        }
        if (stateA == EndpointState.NOT_LOADED || stateB == EndpointState.NOT_LOADED) {
            return;
        }

        SteelCableRope rope = ropes.get(strand.id);
        if (rope == null) {
            if (strand.points != null && strand.points.size() >= 2) {
                rope = SteelCableRope.restore(strand.id, strand.type, strand.restLength,
                        strand.subA, strand.localA, strand.subB, strand.localB, strand.points, strand.extensionGoal);
            } else {
                Vector3d worldA = StrandEndpoints.worldPoint(level, container, strand.subA, strand.localA);
                Vector3d worldB = StrandEndpoints.worldPoint(level, container, strand.subB, strand.localB);
                if (worldA == null || worldB == null) {
                    return;
                }
                rope = SteelCableRope.build(strand.id, strand.type, strand.restLength,
                        strand.subA, strand.localA, worldA, strand.subB, strand.localB, worldB);
            }
            ropes.add(rope);
        }

        if (!rope.isActive() && rope.areAttachmentsLoaded(level)
                && system.getTicketManager().wouldBeLoaded(level, rope)) {
            system.addObject(rope);
        }
        if (!rope.isActive()) {
            return;
        }

        rope.updatePose();
        driveWinch(level, container, strand, rope);
        if (rope.tickSnap()) {
            Vector3d at = breakPoint(level, container, strand);
            if (at != null) {
                snap(level, strands, ropes, strand, at);
                return;
            }
        }
        strand.points = new ArrayList<>(rope.getPoints());
        strand.extensionGoal = rope.getExtension();
        strands.touch();
    }

    // end a is always the winch when there is one, steelcableitem swaps the ends to guarantee it
    private static void driveWinch(ServerLevel level, @Nullable SubLevelContainer container,
                                    SteelCableStrand strand, SteelCableRope rope) {
        BlockEntity be = StrandEndpoints.blockEntity(level, container, strand.subA, strand.localA);
        if (!(be instanceof RopeWinchBlockEntity winch)) {
            return;
        }
        float speed = winch.getMovementSpeed();
        if (rope.isTaut()) {
            speed = Math.max(0.0f, speed);
        }
        if (rope.getCurrentExtension() > Math.max(strand.type.maxSpan(), strand.restLength)) {
            speed = Math.min(0.0f, speed);
        }
        double extension = rope.getExtension() + speed;
        if (extension < SteelCableRope.MIN_EXTENSION && rope.getPoints().size() == 2) {
            extension = SteelCableRope.MIN_EXTENSION;
        } else {
            while (extension < 0.0) {
                rope.removeFirstPoint();
                extension += 1.0;
                if (extension < SteelCableRope.MIN_EXTENSION && rope.getPoints().size() == 2) {
                    extension = SteelCableRope.MIN_EXTENSION;
                    break;
                }
            }
            while (extension > 1.0) {
                Vector3d anchor = StrandEndpoints.worldPoint(level, container, strand.subA, strand.localA);
                if (anchor == null) {
                    break;
                }
                rope.addPoint(anchor);
                extension -= 1.0;
            }
            if (extension < SteelCableRope.MIN_EXTENSION && rope.getPoints().size() <= 2) {
                extension = SteelCableRope.MIN_EXTENSION;
            }
        }
        rope.updateFirstSegmentExtension(extension);
    }

    private static EndpointState resolveEndpoint(ServerLevel level, @Nullable SubLevelContainer container,
                                                  @Nullable UUID subId, BlockPos local, SteelCableStrand strand) {
        if (RemovedSubLevels.contains(subId)) {
            return EndpointState.GONE;
        }
        if (!StrandEndpoints.isLoaded(level, container, subId, local)) {
            return EndpointState.NOT_LOADED;
        }
        BlockEntity be = StrandEndpoints.blockEntity(level, container, subId, local);
        if (!(be instanceof SteelCableHolder holder) || !holder.holdsStrand(strand.id)) {
            return EndpointState.GONE;
        }
        return EndpointState.OK;
    }

    public static void detach(ServerLevel level, SteelCableStrandManager strands, SteelCableRopeManager ropes,
                               SteelCableStrand strand, @Nullable ServerPlayer player) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (player != null) {
            giveItem(player, strand);
        } else {
            dropItem(level, container, strand);
        }
        release(level, container, strands, ropes, strand);
    }

    public static void snap(ServerLevel level, SteelCableStrandManager strands, SteelCableRopeManager ropes,
                            SteelCableStrand strand, Vector3d at) {
        List<Vector3d> points = strand.points == null ? null : List.copyOf(strand.points);
        release(level, SubLevelContainer.getContainer(level), strands, ropes, strand);
        StrandSnap.snap(level, itemFor(strand), points, at);
    }

    private static void release(ServerLevel level, @Nullable SubLevelContainer container,
                                SteelCableStrandManager strands, SteelCableRopeManager ropes, SteelCableStrand strand) {
        ropes.remove(SubLevelPhysicsSystem.get(level), strand.id);
        strands.remove(strand.id);
        PacketDistributor.sendToPlayersInDimension(level,
                StrandPointsPacket.removal(strand.type.kind(), strand.id));
        detachHolder(level, container, strand.subA, strand.localA, strand.id);
        detachHolder(level, container, strand.subB, strand.localB, strand.id);
    }

    @Nullable
    private static Vector3d snapPoint(ServerLevel level, @Nullable SubLevelContainer container,
                                      SteelCableStrand strand, boolean endAGone) {
        Vector3d end = StrandSnap.endOf(strand.points, endAGone);
        if (end != null) {
            return end;
        }
        return endAGone
                ? StrandEndpoints.worldPoint(level, container, strand.subA, strand.localA)
                : StrandEndpoints.worldPoint(level, container, strand.subB, strand.localB);
    }

    private static ItemStack itemFor(SteelCableStrand strand) {
        return new ItemStack(strand.type.carriesFluid()
                ? LoconauticsRegistries.HOSE.get() : LoconauticsRegistries.STEEL_CABLE.get());
    }

    private static void giveItem(ServerPlayer player, SteelCableStrand strand) {
        ItemStack stack = itemFor(strand);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void dropItem(ServerLevel level, @Nullable SubLevelContainer container, SteelCableStrand strand) {
        Vector3d dropAt = breakPoint(level, container, strand);
        if (dropAt == null) {
            return;
        }
        Block.popResource(level, BlockPos.containing(dropAt.x, dropAt.y, dropAt.z),
                itemFor(strand));
    }

    @Nullable
    private static Vector3d breakPoint(ServerLevel level, @Nullable SubLevelContainer container, SteelCableStrand strand) {
        List<Vector3d> points = strand.points;
        if (points != null && !points.isEmpty()) {
            return points.get(points.size() / 2);
        }
        Vector3d worldA = StrandEndpoints.worldPoint(level, container, strand.subA, strand.localA);
        return worldA != null ? worldA : StrandEndpoints.worldPoint(level, container, strand.subB, strand.localB);
    }

    private static void detachHolder(ServerLevel level, @Nullable SubLevelContainer container,
                                      @Nullable UUID subId, BlockPos local, UUID strandId) {
        BlockEntity be = StrandEndpoints.blockEntity(level, container, subId, local);
        if (be instanceof SteelCableHolder holder) {
            holder.onStrandDetached(strandId);
        }
    }

    public static void breakStrand(ServerLevel level, SteelCableStrandManager strands, SteelCableRopeManager ropes,
                                    SteelCableStrand strand, @Nullable ServerPlayer player) {
        List<Vector3d> points = strand.points;
        if (points != null) {
            ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, itemFor(strand));
            for (Vector3d p : points) {
                level.playSound(null, p.x, p.y, p.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.5f, 1.0f);
                level.sendParticles(particle, p.x, p.y, p.z, 10, 0.1, 0.1, 0.1, 0.1);
            }
        }
        detach(level, strands, ropes, strand, player);
    }
}
