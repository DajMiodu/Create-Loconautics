package com.lycoris.loconautics.network.packets.variantpicker;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.conductorshat.ConductorsHatItem;
import com.lycoris.loconautics.content.conductorshat.ConductorsHats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HatColorApplyPacket(InteractionHand hand, int colorId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HatColorApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("hat_color_apply"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, InteractionHand> HAND_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, HatColorApplyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    HAND_CODEC,          HatColorApplyPacket::hand,
                    ByteBufCodecs.VAR_INT, HatColorApplyPacket::colorId,
                    HatColorApplyPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HatColorApplyPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            ItemStack held = player.getItemInHand(packet.hand());
            if (!(held.getItem() instanceof ConductorsHatItem current)) return;
            DyeColor chosen = DyeColor.byId(packet.colorId());
            if (chosen == current.getColor()) return;
            var hat = ConductorsHats.HATS.get(chosen);
            if (hat == null) return;
            player.setItemInHand(packet.hand(), new ItemStack(hat.get(), held.getCount()));
        });
    }
}
