package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelvalve.ValveScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ValveOpenPacket(BlockPos panelPos, int slot, float degrees) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ValveOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("valve_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ValveOpenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ValveOpenPacket::panelPos,
                    ByteBufCodecs.VAR_INT, ValveOpenPacket::slot,
                    ByteBufCodecs.FLOAT, ValveOpenPacket::degrees,
                    ValveOpenPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ValveOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.slot() < 0 || packet.slot() >= PanelSlot.values().length) return;
            ValveScreen.open(packet.panelPos(), PanelSlot.values()[packet.slot()], packet.degrees());
        });
    }
}
