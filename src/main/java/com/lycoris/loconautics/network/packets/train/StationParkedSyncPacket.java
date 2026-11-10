package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record StationParkedSyncPacket(BlockPos pos, String name, List<Integer> carriages,
                                      int mapColorIndex, String iconType, float approach)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StationParkedSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("station_parked_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationParkedSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StationParkedSyncPacket::pos,
                    ByteBufCodecs.STRING_UTF8, StationParkedSyncPacket::name,
                    ByteBufCodecs.INT.apply(ByteBufCodecs.list()), StationParkedSyncPacket::carriages,
                    ByteBufCodecs.INT, StationParkedSyncPacket::mapColorIndex,
                    ByteBufCodecs.stringUtf8(StationSetTrainColorPacket.MAX_ICON_ID_LENGTH),
                    StationParkedSyncPacket::iconType,
                    ByteBufCodecs.FLOAT, StationParkedSyncPacket::approach,
                    StationParkedSyncPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StationParkedSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                SableTrainClientRegistry.setStationParked(
                        packet.pos(), packet.approach() == 0.0f, packet.name(), packet.carriages(),
                        packet.mapColorIndex(), packet.iconType(), packet.approach()));
    }

    public static void broadcast(BlockPos pos, boolean parked, String name, List<Integer> carriages,
                                 int mapColorIndex, String iconType, float approach) {
        PacketDistributor.sendToAllPlayers(new StationParkedSyncPacket(
                pos.immutable(), name == null ? "" : name,
                carriages == null ? List.of() : List.copyOf(carriages), mapColorIndex,
                iconType == null ? "" : iconType, parked ? 0.0f : approach));
    }

    public static void clear(BlockPos pos) {
        broadcast(pos, false, "", List.of(), 0, "", Float.MAX_VALUE);
    }
}
