package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.StrandEnd;
import com.lycoris.loconautics.common.strand.StrandKind;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record StrandPointsPacket(StrandKind kind, UUID strandUUID, int interpolationTick, boolean stopped,
                                 List<Vector3d> points, @Nullable StrandEnd start, @Nullable StrandEnd end)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrandPointsPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("strand_points"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrandPointsPacket> STREAM_CODEC =
            StreamCodec.of(StrandPointsPacket::write, StrandPointsPacket::read);

    // an empty point list means the strand is gone, so a break needs no second packet type
    public static StrandPointsPacket removal(StrandKind kind, UUID strandUUID) {
        return new StrandPointsPacket(kind, strandUUID, 0, true, List.of(), null, null);
    }

    private static void write(RegistryFriendlyByteBuf buf, StrandPointsPacket packet) {
        buf.writeEnum(packet.kind());
        buf.writeUUID(packet.strandUUID());
        buf.writeInt(packet.interpolationTick());
        buf.writeBoolean(packet.stopped());
        buf.writeVarInt(packet.points().size());
        for (Vector3d p : packet.points()) {
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
        }
        writeEnd(buf, packet.start());
        writeEnd(buf, packet.end());
    }

    private static void writeEnd(RegistryFriendlyByteBuf buf, @Nullable StrandEnd end) {
        buf.writeBoolean(end != null);
        if (end == null) {
            return;
        }
        buf.writeBoolean(end.subLevelId() != null);
        if (end.subLevelId() != null) {
            buf.writeUUID(end.subLevelId());
        }
        buf.writeBlockPos(end.local());
    }

    @Nullable
    private static StrandEnd readEnd(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        UUID sub = buf.readBoolean() ? buf.readUUID() : null;
        return new StrandEnd(sub, buf.readBlockPos());
    }

    private static StrandPointsPacket read(RegistryFriendlyByteBuf buf) {
        StrandKind kind = buf.readEnum(StrandKind.class);
        UUID id = buf.readUUID();
        int tick = buf.readInt();
        boolean stopped = buf.readBoolean();
        int count = buf.readVarInt();
        List<Vector3d> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        StrandEnd start = readEnd(buf);
        return new StrandPointsPacket(kind, id, tick, stopped, points, start, readEnd(buf));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StrandPointsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> packet.kind().clientStrands().accept(
                packet.strandUUID(), packet.interpolationTick(), packet.stopped(), packet.points(), packet.start(), packet.end()));
    }
}
