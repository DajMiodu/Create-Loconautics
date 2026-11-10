package com.lycoris.loconautics.network.packets.pullbrake;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.brakes.pull.PullBrakeBlockEntity;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PullBrakeSetPacket(BlockPos pos, int power) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PullBrakeSetPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("pull_brake_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullBrakeSetPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PullBrakeSetPacket::pos,
                    ByteBufCodecs.VAR_INT, PullBrakeSetPacket::power,
                    PullBrakeSetPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PullBrakeSetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PullBrakeBlockEntity brake = PacketTargets.blockEntity(player.level(), packet.pos(),
                    PullBrakeBlockEntity.class);
            if (brake != null && PacketTargets.withinReach(player, brake)) {
                brake.setPower(packet.power());
            }
        });
    }
}
