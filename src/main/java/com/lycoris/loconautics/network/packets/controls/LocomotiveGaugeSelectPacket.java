package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LocomotiveGaugeSelectPacket(BlockPos gaugePos, int slot, GaugeMetric metric,
                                          int background, int front, int needleColor, int options)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LocomotiveGaugeSelectPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("locomotive_gauge_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocomotiveGaugeSelectPacket> STREAM_CODEC =
            StreamCodec.of(LocomotiveGaugeSelectPacket::write, LocomotiveGaugeSelectPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, LocomotiveGaugeSelectPacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.gaugePos());
        buf.writeVarInt(packet.slot());
        GaugeMetric.STREAM_CODEC.encode(buf, packet.metric());
        buf.writeVarInt(packet.background());
        buf.writeVarInt(packet.front());
        buf.writeVarInt(packet.needleColor());
        buf.writeVarInt(packet.options());
    }

    private static LocomotiveGaugeSelectPacket read(RegistryFriendlyByteBuf buf) {
        return new LocomotiveGaugeSelectPacket(BlockPos.STREAM_CODEC.decode(buf), buf.readVarInt(),
                GaugeMetric.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LocomotiveGaugeSelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.slot() < 0 || packet.slot() >= PanelSlot.values().length) return;
            GaugeBlockEntity gauge = PacketTargets.blockEntity(
                    player.level(), packet.gaugePos(), GaugeBlockEntity.class);
            if (gauge == null || !PacketTargets.withinReach(player, gauge)) return;
            gauge.configure(PanelSlot.values()[packet.slot()], packet.metric(),
                    packet.background(), packet.front(), packet.needleColor(), packet.options());
        });
    }
}
