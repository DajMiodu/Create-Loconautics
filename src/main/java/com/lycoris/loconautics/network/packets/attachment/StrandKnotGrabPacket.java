package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.AttachedRopePhysicsObject;
import com.lycoris.loconautics.common.strand.RopeManager;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record StrandKnotGrabPacket(UUID strandUUID, int knotIndex, boolean stop, float range)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrandKnotGrabPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("strand_knot_grab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrandKnotGrabPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.strandUUID());
                        buf.writeVarInt(p.knotIndex());
                        buf.writeBoolean(p.stop());
                        buf.writeFloat(p.range());
                    },
                    buf -> new StrandKnotGrabPacket(buf.readUUID(), buf.readVarInt(), buf.readBoolean(),
                            buf.readFloat()));

    public static StrandKnotGrabPacket stopping(UUID strandUUID) {
        return new StrandKnotGrabPacket(strandUUID, 0, true, 0.0f);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StrandKnotGrabPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!PacketTargets.finite(packet.range())) {
                return;
            }
            AttachedRopePhysicsObject rope = RopeManager.find(level, packet.strandUUID());
            if (rope == null) {
                return;
            }
            if (packet.stop()) {
                rope.knotGrab().stop(player.getUUID());
                return;
            }
            rope.knotGrab().request(player.getUUID(), packet.knotIndex(), packet.range());
        });
    }
}
