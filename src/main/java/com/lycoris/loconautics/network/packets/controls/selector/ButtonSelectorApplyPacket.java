package com.lycoris.loconautics.network.packets.controls.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.network.packets.controls.PanelPartEdit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ButtonSelectorApplyPacket(BlockPos panelPos, int slot, int body, int capColor, int style, int options)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ButtonSelectorApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("button_selector_apply"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ButtonSelectorApplyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ButtonSelectorApplyPacket::panelPos,
                    ByteBufCodecs.VAR_INT, ButtonSelectorApplyPacket::slot,
                    ByteBufCodecs.VAR_INT, ButtonSelectorApplyPacket::body,
                    ByteBufCodecs.VAR_INT, ButtonSelectorApplyPacket::capColor,
                    ByteBufCodecs.VAR_INT, ButtonSelectorApplyPacket::style,
                    ByteBufCodecs.VAR_INT, ButtonSelectorApplyPacket::options,
                    ButtonSelectorApplyPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ButtonSelectorApplyPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), ButtonPart.class,
                (player, panel, slot, button) -> panel.replacePart(slot,
                        new ButtonPart(button.pressedUntil(), button.link(),
                                PanelPartType.clampBody(packet.body()),
                                PanelPartType.clampColor(packet.capColor()),
                                PanelStyles.BUTTON.clamp(packet.style()),
                                PanelStyles.BUTTON.clampOptions(packet.options()))));
    }
}
