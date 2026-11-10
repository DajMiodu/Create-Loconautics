package com.lycoris.loconautics.network.packets.controls.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.lycoris.loconautics.network.packets.controls.PanelPartEdit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ValveSelectorApplyPacket(BlockPos panelPos, int slot, int wheelColor) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ValveSelectorApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("valve_selector_apply"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ValveSelectorApplyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ValveSelectorApplyPacket::panelPos,
                    ByteBufCodecs.VAR_INT, ValveSelectorApplyPacket::slot,
                    ByteBufCodecs.VAR_INT, ValveSelectorApplyPacket::wheelColor,
                    ValveSelectorApplyPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ValveSelectorApplyPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), ValvePart.class,
                (player, panel, slot, valve) -> panel.replacePart(slot,
                        new ValvePart(valve.degrees(), valve.safetyValve(),
                                PanelPartType.clampColor(packet.wheelColor()))));
    }
}
