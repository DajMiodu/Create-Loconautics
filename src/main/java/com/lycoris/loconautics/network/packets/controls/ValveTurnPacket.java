package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ValveTurnPacket(BlockPos panelPos, int slot, float degrees) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ValveTurnPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("valve_turn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ValveTurnPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ValveTurnPacket::panelPos,
                    ByteBufCodecs.VAR_INT, ValveTurnPacket::slot,
                    ByteBufCodecs.FLOAT, ValveTurnPacket::degrees,
                    ValveTurnPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ValveTurnPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), ValvePart.class, (player, panel, slot, valve) -> {
            float degrees = ValvePart.clamp(packet.degrees());
            panel.replacePart(slot, new ValvePart(degrees, valve.safetyValve(), valve.wheelColor()));
            if ((int) (degrees / ValvePart.SNAP) != (int) (valve.degrees() / ValvePart.SNAP)) {
                player.level().playSound(null, packet.panelPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                        0.2f, 0.6f + degrees / ValvePart.FULL_TURN * 0.5f);
            }
        });
    }
}
