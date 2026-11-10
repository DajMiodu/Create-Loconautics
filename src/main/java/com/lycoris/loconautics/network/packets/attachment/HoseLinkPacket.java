package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.HoseItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HoseLinkPacket(BlockPos first, BlockPos second) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HoseLinkPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("hose_link"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HoseLinkPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.first());
                        buf.writeBlockPos(p.second());
                    },
                    buf -> new HoseLinkPacket(buf.readBlockPos(), buf.readBlockPos()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HoseLinkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (!(stack.getItem() instanceof HoseItem hose)) {
                    continue;
                }
                if (hose.createSteelCable(level, packet.first(), packet.second()) && !player.isCreative()) {
                    stack.shrink(1);
                }
                return;
            }
        });
    }
}
