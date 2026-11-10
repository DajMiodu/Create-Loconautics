package com.lycoris.loconautics.network.packets.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConductorHatPacket(int entityId, boolean conductor) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConductorHatPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("conductor_hat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConductorHatPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ConductorHatPacket::entityId,
                    ByteBufCodecs.BOOL,    ConductorHatPacket::conductor,
                    ConductorHatPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConductorHatPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;
            Entity entity = Minecraft.getInstance().level.getEntity(packet.entityId());
            if (entity == null) return;
            if (packet.conductor()) {
                entity.getPersistentData().putBoolean("TrainHat", true);
            } else {
                entity.getPersistentData().remove("TrainHat");
            }
        });
    }
}
