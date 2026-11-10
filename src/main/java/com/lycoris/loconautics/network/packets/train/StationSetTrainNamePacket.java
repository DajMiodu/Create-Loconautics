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

public record StationSetTrainNamePacket(BlockPos stationPos, String name) implements CustomPacketPayload {

    public static final int MAX_NAME_LENGTH = 64;

    public static final CustomPacketPayload.Type<StationSetTrainNamePacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("station_set_train_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationSetTrainNamePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StationSetTrainNamePacket::stationPos,
                    ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), StationSetTrainNamePacket::name,
                    StationSetTrainNamePacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StationSetTrainNamePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PacketTargets.canEditStation(player, packet.stationPos())) return;
            SableTrainSpawner.setTrainNameFromStation(player, packet.stationPos(), packet.name());
        });
    }
}
