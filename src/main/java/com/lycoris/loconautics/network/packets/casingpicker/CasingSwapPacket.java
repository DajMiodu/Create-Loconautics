package com.lycoris.loconautics.network.packets.casingpicker;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.casingpicker.BogeyChoice;
import com.lycoris.loconautics.content.casingpicker.BogeyItem;
import com.simibubi.create.AllBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CasingSwapPacket(boolean toBogey) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CasingSwapPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("casing_swap"));

    public static final StreamCodec<ByteBuf, CasingSwapPacket> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(CasingSwapPacket::new, CasingSwapPacket::toBogey);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CasingSwapPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            ItemStack held = player.getMainHandItem();
            if (packet.toBogey() && AllBlocks.RAILWAY_CASING.isIn(held)) {
                player.setItemInHand(InteractionHand.MAIN_HAND,
                        BogeyItem.of(BogeyChoice.of(player.getUUID()), held.getCount()));
            } else if (!packet.toBogey() && held.getItem() instanceof BogeyItem) {
                player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.RAILWAY_CASING.asStack(held.getCount()));
            }
        });
    }
}
