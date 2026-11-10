package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.network.PacketTargets;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StationSetTrainColorPacket(BlockPos stationPos, int mapColorIndex, String iconType)
        implements CustomPacketPayload {

    public static final int MAX_ICON_ID_LENGTH = 128;

    public static final CustomPacketPayload.Type<StationSetTrainColorPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("station_set_train_color"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationSetTrainColorPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StationSetTrainColorPacket::stationPos,
                    ByteBufCodecs.INT, StationSetTrainColorPacket::mapColorIndex,
                    ByteBufCodecs.stringUtf8(MAX_ICON_ID_LENGTH), StationSetTrainColorPacket::iconType,
                    StationSetTrainColorPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StationSetTrainColorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PacketTargets.canEditStation(player, packet.stationPos())) return;
            SableTrainSpawner.setTrainStyleFromStation(player, packet.stationPos(), packet.mapColorIndex(),
                    packet.iconType());
        });
    }
}
