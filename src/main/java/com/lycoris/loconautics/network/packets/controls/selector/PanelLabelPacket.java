package com.lycoris.loconautics.network.packets.controls.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.network.packets.controls.PanelPartEdit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PanelLabelPacket(BlockPos panelPos, int slot, String raw) implements CustomPacketPayload {

    public static final int MAX_LENGTH = 32;

    public static final CustomPacketPayload.Type<PanelLabelPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("panel_label"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelLabelPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PanelLabelPacket::panelPos,
                    ByteBufCodecs.VAR_INT, PanelLabelPacket::slot,
                    ByteBufCodecs.stringUtf8(MAX_LENGTH), PanelLabelPacket::raw,
                    PanelLabelPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PanelLabelPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), PanelPart.class,
                (player, panel, slot, part) -> panel.setLabel(slot,
                        packet.raw().isBlank() ? null : Component.literal(packet.raw())));
    }
}
