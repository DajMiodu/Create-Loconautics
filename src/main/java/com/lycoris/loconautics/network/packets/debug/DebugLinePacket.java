package com.lycoris.loconautics.network.packets.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.debug.DebugLinesClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DebugLinePacket(String slot, Vec3 start, Vec3 end, int color, float width, int ttl)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DebugLinePacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("debug_line"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugLinePacket> STREAM_CODEC =
            StreamCodec.of(DebugLinePacket::write, DebugLinePacket::read);

    private static void write(RegistryFriendlyByteBuf buf, DebugLinePacket packet) {
        buf.writeUtf(packet.slot());
        writeVec(buf, packet.start());
        writeVec(buf, packet.end());
        buf.writeInt(packet.color());
        buf.writeFloat(packet.width());
        buf.writeVarInt(packet.ttl());
    }

    private static DebugLinePacket read(RegistryFriendlyByteBuf buf) {
        return new DebugLinePacket(buf.readUtf(), readVec(buf), readVec(buf),
                buf.readInt(), buf.readFloat(), buf.readVarInt());
    }

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DebugLinePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DebugLinesClient.put(packet.slot(), packet.start(), packet.end(),
                packet.color(), packet.width(), packet.ttl()));
    }
}
