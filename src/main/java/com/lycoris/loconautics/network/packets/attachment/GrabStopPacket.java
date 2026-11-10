package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.grab.GrabbableActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record GrabStopPacket(UUID subLevelUUID, BlockPos nozzleLocalPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GrabStopPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("grab_stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrabStopPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.subLevelUUID());
                        buf.writeBlockPos(p.nozzleLocalPos());
                    },
                    buf -> new GrabStopPacket(buf.readUUID(), buf.readBlockPos()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GrabStopPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null
                    && container.getSubLevel(packet.subLevelUUID()) instanceof ServerSubLevel sub
                    && sub.getLevel().getBlockEntity(packet.nozzleLocalPos()) instanceof GrabbableActor grabbable) {
                grabbable.stopGrab(player.getUUID());
            }
        });
    }
}
