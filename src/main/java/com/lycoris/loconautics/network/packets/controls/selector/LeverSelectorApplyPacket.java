package com.lycoris.loconautics.network.packets.controls.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.network.packets.controls.PanelPartEdit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LeverSelectorApplyPacket(BlockPos panelPos, int slot, int body, int knobColor, int style, int options)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LeverSelectorApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("lever_selector_apply"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeverSelectorApplyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, LeverSelectorApplyPacket::panelPos,
                    ByteBufCodecs.VAR_INT, LeverSelectorApplyPacket::slot,
                    ByteBufCodecs.VAR_INT, LeverSelectorApplyPacket::body,
                    ByteBufCodecs.VAR_INT, LeverSelectorApplyPacket::knobColor,
                    ByteBufCodecs.VAR_INT, LeverSelectorApplyPacket::style,
                    ByteBufCodecs.VAR_INT, LeverSelectorApplyPacket::options,
                    LeverSelectorApplyPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LeverSelectorApplyPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), LeverPart.class,
                (player, panel, slot, lever) -> panel.replacePart(slot,
                        new LeverPart(lever.power(), lever.link(),
                                PanelPartType.clampBody(packet.body()),
                                PanelPartType.clampColor(packet.knobColor()),
                                PanelStyles.LEVER.clamp(packet.style()),
                                PanelStyles.LEVER.clampOptions(packet.options()))));
    }
}
