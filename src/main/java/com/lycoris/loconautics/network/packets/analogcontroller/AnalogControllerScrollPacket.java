package com.lycoris.loconautics.network.packets.analogcontroller;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnalogControllerScrollPacket(int delta, BlockPos pos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnalogControllerScrollPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("analog_controller_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogControllerScrollPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,    AnalogControllerScrollPacket::delta,
                    BlockPos.STREAM_CODEC,    AnalogControllerScrollPacket::pos,
                    AnalogControllerScrollPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AnalogControllerScrollPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            AnalogControllerBlockEntity ace = PacketTargets.blockEntity(
                    player.level(), packet.pos(), AnalogControllerBlockEntity.class);
            if (ace != null)
                ace.onScroll(player.getUUID(), packet.delta());
        });
    }
}