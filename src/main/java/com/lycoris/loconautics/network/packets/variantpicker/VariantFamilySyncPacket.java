package com.lycoris.loconautics.network.packets.variantpicker;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.variantpicker.BlockVariantRegistry;
import com.lycoris.loconautics.content.variantpicker.data.LoadedVariantFamily;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record VariantFamilySyncPacket(List<LoadedVariantFamily> families) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VariantFamilySyncPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("variant_family_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VariantFamilySyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> LoadedVariantFamily.write(buf, packet.families()),
                    buf -> new VariantFamilySyncPacket(LoadedVariantFamily.read(buf))
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // never onlyin, a dedicated server strips the method and the playtoclient registration then cannot resolve it
    public static void handle(VariantFamilySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> BlockVariantRegistry.rebuild(packet.families()));
    }
}
