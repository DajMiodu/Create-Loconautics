package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.flow.ClientHoseFlow;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

public record HoseFlowPacket(List<UUID> forward, List<UUID> backward) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HoseFlowPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("hose_flow"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HoseFlowPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), HoseFlowPacket::forward,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), HoseFlowPacket::backward,
            HoseFlowPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HoseFlowPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHoseFlow.accept(packet.forward(), packet.backward()));
    }
}
