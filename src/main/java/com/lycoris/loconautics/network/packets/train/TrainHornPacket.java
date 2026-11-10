package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.WhistleHonks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrainHornPacket(BlockPos whistle, boolean honk) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TrainHornPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("train_horn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrainHornPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TrainHornPacket::whistle,
                    ByteBufCodecs.BOOL, TrainHornPacket::honk,
                    TrainHornPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrainHornPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> WhistleHonks.honk(packet.whistle(), packet.honk()));
    }
}
