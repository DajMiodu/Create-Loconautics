package com.lycoris.loconautics.network.packets.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.map.client.FullscreenMapScreen;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record MapColorResponsePacket(List<Entry> entries) implements CustomPacketPayload {

    public record Entry(int chunkX, int chunkZ, byte[] colors) {}

    public static final CustomPacketPayload.Type<MapColorResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("map_color_response"));

    public static final StreamCodec<ByteBuf, MapColorResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapColorResponsePacket decode(ByteBuf buf) {
            int size = buf.readInt();
            if (size < 0 || size > MapColorRequestPacket.MAX_CHUNKS) {
                throw new DecoderException("map color response of " + size + " chunks");
            }
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int cx = buf.readInt(), cz = buf.readInt();
                byte[] colors = new byte[256];
                buf.readBytes(colors);
                entries.add(new Entry(cx, cz, colors));
            }
            return new MapColorResponsePacket(entries);
        }

        @Override
        public void encode(ByteBuf buf, MapColorResponsePacket packet) {
            buf.writeInt(packet.entries().size());
            for (Entry e : packet.entries()) {
                buf.writeInt(e.chunkX());
                buf.writeInt(e.chunkZ());
                buf.writeBytes(e.colors());
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MapColorResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> FullscreenMapScreen.acceptColors(packet.entries()));
    }
}
