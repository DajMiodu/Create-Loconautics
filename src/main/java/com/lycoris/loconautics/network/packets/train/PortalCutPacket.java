package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.portal.PortalCut;
import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record PortalCutPacket(UUID subLevelId, boolean active, int axis, int cutoff, boolean positive)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PortalCutPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("portal_cut"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalCutPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.subLevelId());
                        buf.writeBoolean(p.active());
                        buf.writeByte(p.axis());
                        buf.writeVarInt(p.cutoff());
                        buf.writeBoolean(p.positive());
                    },
                    buf -> new PortalCutPacket(buf.readUUID(), buf.readBoolean(), buf.readByte(), buf.readVarInt(),
                            buf.readBoolean())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortalCutPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.active()) {
                PortalCutsClient.put(new PortalCut(packet.subLevelId(), packet.axis(), packet.cutoff(),
                        packet.positive()));
            } else {
                PortalCutsClient.remove(packet.subLevelId());
            }
        });
    }

    public static void broadcast(PortalCut cut) {
        PacketDistributor.sendToAllPlayers(new PortalCutPacket(cut.subLevelId(), true, cut.axis(), cut.cutoff(),
                cut.positive()));
    }

    public static void broadcastClear(UUID subLevelId) {
        PacketDistributor.sendToAllPlayers(new PortalCutPacket(subLevelId, false, 0, 0, true));
    }
}
