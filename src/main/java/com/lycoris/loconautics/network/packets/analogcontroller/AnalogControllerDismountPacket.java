package com.lycoris.loconautics.network.packets.analogcontroller;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnalogControllerDismountPacket(BlockPos controllerPos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnalogControllerDismountPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("analog_controller_dismount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogControllerDismountPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AnalogControllerDismountPacket::controllerPos,
                    AnalogControllerDismountPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnalogControllerDismountPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            AnalogControllerBlockEntity ace = PacketTargets.blockEntity(
                    player.level(), packet.controllerPos(), AnalogControllerBlockEntity.class);
            if (ace != null) {
                ace.releaseUser(player.getUUID());
            }
        });
    }
}