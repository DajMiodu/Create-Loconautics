package com.lycoris.loconautics.network.packets.controls.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.network.packets.controls.PanelPartEdit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwitchSelectorApplyPacket(BlockPos panelPos, int slot, int body, int knobColor, int style, int options)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SwitchSelectorApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("switch_selector_apply"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchSelectorApplyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SwitchSelectorApplyPacket::panelPos,
                    ByteBufCodecs.VAR_INT, SwitchSelectorApplyPacket::slot,
                    ByteBufCodecs.VAR_INT, SwitchSelectorApplyPacket::body,
                    ByteBufCodecs.VAR_INT, SwitchSelectorApplyPacket::knobColor,
                    ByteBufCodecs.VAR_INT, SwitchSelectorApplyPacket::style,
                    ByteBufCodecs.VAR_INT, SwitchSelectorApplyPacket::options,
                    SwitchSelectorApplyPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwitchSelectorApplyPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), SwitchPart.class,
                (player, panel, slot, part) -> panel.replacePart(slot,
                        new SwitchPart(part.power(), part.link(),
                                PanelPartType.clampBody(packet.body()),
                                PanelPartType.clampColor(packet.knobColor()),
                                PanelStyles.SWITCH.clamp(packet.style()),
                                PanelStyles.SWITCH.clampOptions(packet.options()))));
    }
}
