package com.lycoris.loconautics.network.packets.analogcontroller;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnalogControllerMountPacket(boolean mounted, BlockPos pos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnalogControllerMountPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("analog_controller_mount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogControllerMountPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,       AnalogControllerMountPacket::mounted,
                    BlockPos.STREAM_CODEC,    AnalogControllerMountPacket::pos,
                    AnalogControllerMountPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnalogControllerMountPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.mounted()) {
                AnalogControllerClientHandler.startControlling(packet.pos());
            } else {
                AnalogControllerClientHandler.stopControlling();
            }
        });
    }
}