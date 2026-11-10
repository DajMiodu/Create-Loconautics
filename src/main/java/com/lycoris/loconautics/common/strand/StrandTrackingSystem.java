package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelTrackingPlugin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class StrandTrackingSystem<R extends AttachedRopePhysicsObject> implements SubLevelTrackingPlugin {

    private final ServerLevel level;
    private final Function<R, StrandKind> kindOf;
    private final Function<Level, RopeManager<R>> ropes;

    public StrandTrackingSystem(ServerLevel level, Function<R, StrandKind> kindOf,
                                Function<Level, RopeManager<R>> ropes) {
        this.level = level;
        this.kindOf = kindOf;
        this.ropes = ropes;
    }

    @Override
    public Iterable<UUID> neededPlayers() {
        RopeManager<R> manager = ropes.apply(level);
        boolean moving = false;
        if (manager != null) {
            for (R rope : manager.all()) {
                StrandKnotGrab grab = rope.knotGrab();
                if (rope.isActive()) {
                    rope.updatePose();
                    moving |= grab.engaged() || rope.needsSync();
                }
            }
        }
        if (!moving) {
            return List.of();
        }
        return level.players().stream().map(Player::getUUID).toList();
    }

    @Override
    public void sendTrackingData(int interpolationTick) {
        RopeManager<R> manager = ropes.apply(level);
        if (manager == null) {
            return;
        }
        for (R rope : manager.all()) {
            if (!rope.isActive()) {
                continue;
            }
            send(rope, interpolationTick);
        }
    }

    private void send(@Nullable R rope, int interpolationTick) {
        if (rope.needsSync()) {
            rope.setNetworkingStopped(false);
            PacketDistributor.sendToPlayersInDimension(level, new StrandPointsPacket(
                    kindOf.apply(rope), rope.getUUID(), interpolationTick, false, List.copyOf(rope.getPoints()),
                    rope.endOf(RopeHandle.AttachmentPoint.START), rope.endOf(RopeHandle.AttachmentPoint.END)));
            rope.justSynced();
            return;
        }
        if (rope.isNetworkingStopped()) {
            return;
        }
        rope.setNetworkingStopped(true);
        PacketDistributor.sendToPlayersInDimension(level, new StrandPointsPacket(
                kindOf.apply(rope), rope.getUUID(), interpolationTick, true, List.copyOf(rope.getPoints()),
                    rope.endOf(RopeHandle.AttachmentPoint.START), rope.endOf(RopeHandle.AttachmentPoint.END)));
    }
}
