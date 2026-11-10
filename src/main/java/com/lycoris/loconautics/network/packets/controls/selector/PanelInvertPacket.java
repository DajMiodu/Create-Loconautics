package com.lycoris.loconautics.network.packets.controls.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.RedstoneLinkedPart;
import com.lycoris.loconautics.network.packets.controls.PanelPartEdit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PanelInvertPacket(BlockPos panelPos, int slot, boolean inverted) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PanelInvertPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("panel_invert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelInvertPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PanelInvertPacket::panelPos,
                    ByteBufCodecs.VAR_INT, PanelInvertPacket::slot,
                    ByteBufCodecs.BOOL, PanelInvertPacket::inverted,
                    PanelInvertPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PanelInvertPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), RedstoneLinkedPart.class,
                (player, panel, slot, part) -> panel.setInverted(slot, packet.inverted()));
    }
}
