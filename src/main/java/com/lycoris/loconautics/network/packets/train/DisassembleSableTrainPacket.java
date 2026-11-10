package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.network.PacketTargets;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record DisassembleSableTrainPacket(BlockPos stationPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DisassembleSableTrainPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("disassemble_sable_train"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisassembleSableTrainPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DisassembleSableTrainPacket::stationPos,
                    DisassembleSableTrainPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisassembleSableTrainPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PacketTargets.canEditStation(player, packet.stationPos())) return;
            if (SableTrainSpawner.disassembleFromStation(player, packet.stationPos())) {
                StationParkedSyncPacket.clear(packet.stationPos());
            }
        });
    }
}
