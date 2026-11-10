package com.lycoris.loconautics.network.packets.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.ConductorHats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record BlazeConductorHatPacket(UUID subLevelId, BlockPos pos, boolean hat) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlazeConductorHatPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("blaze_conductor_hat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlazeConductorHatPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, BlazeConductorHatPacket::subLevelId,
                    BlockPos.STREAM_CODEC,  BlazeConductorHatPacket::pos,
                    ByteBufCodecs.BOOL,     BlazeConductorHatPacket::hat,
                    BlazeConductorHatPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlazeConductorHatPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ConductorHats.setDriver(packet.subLevelId(), packet.pos(), packet.hat()));
    }
}
