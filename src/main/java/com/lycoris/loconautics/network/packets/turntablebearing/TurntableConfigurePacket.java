package com.lycoris.loconautics.network.packets.turntablebearing;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlockEntity;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record TurntableConfigurePacket(BlockPos pos, List<TurntableInstruction> instructions,
                                        TurntableTriggerMode trigger) implements CustomPacketPayload {

    private static final double MAX_REACH_SQUARED = 64.0;

    public static final CustomPacketPayload.Type<TurntableConfigurePacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("turntable_configure"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TurntableConfigurePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TurntableConfigurePacket::pos,
                    TurntableInstruction.LIST_STREAM_CODEC, TurntableConfigurePacket::instructions,
                    TurntableTriggerMode.STREAM_CODEC, TurntableConfigurePacket::trigger,
                    TurntableConfigurePacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TurntableConfigurePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.isSpectator()) return;
            if (player.distanceToSqr(packet.pos().getCenter()) > MAX_REACH_SQUARED) return;
            TurntableBearingBlockEntity bearing = PacketTargets.blockEntity(
                    player.level(), packet.pos(), TurntableBearingBlockEntity.class);
            if (bearing == null) return;
            bearing.applyConfiguration(packet.instructions(), packet.trigger());
        });
    }
}
