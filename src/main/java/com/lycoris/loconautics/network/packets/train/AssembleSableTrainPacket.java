package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.network.PacketTargets;
import com.lycoris.loconautics.trainphysics.spawn.StationAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AssembleSableTrainPacket(BlockPos stationPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AssembleSableTrainPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("assemble_sable_train"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssembleSableTrainPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AssembleSableTrainPacket::stationPos,
                    AssembleSableTrainPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AssembleSableTrainPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PacketTargets.canEditStation(player, packet.stationPos())) return;
            StationAssembly.assembleFromStation(player, packet.stationPos());
        });
    }
}
