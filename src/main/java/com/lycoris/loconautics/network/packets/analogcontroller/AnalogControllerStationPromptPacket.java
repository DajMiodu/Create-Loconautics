package com.lycoris.loconautics.network.packets.analogcontroller;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerHUD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnalogControllerStationPromptPacket(Component text, boolean shadow)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnalogControllerStationPromptPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("analog_controller_station_prompt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogControllerStationPromptPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.STREAM_CODEC, AnalogControllerStationPromptPacket::text,
                    ByteBufCodecs.BOOL,                  AnalogControllerStationPromptPacket::shadow,
                    AnalogControllerStationPromptPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnalogControllerStationPromptPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            AnalogControllerHUD.currentPrompt = packet.text();
            AnalogControllerHUD.currentPromptShadow = packet.shadow();
            AnalogControllerHUD.promptKeepAlive = 30;
        });
    }
}
