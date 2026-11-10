package com.lycoris.loconautics.network.packets.variantpicker;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.variantpicker.BlockVariantRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BlockVariantApplyPacket(InteractionHand hand, ResourceLocation blockId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlockVariantApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("block_variant_apply"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, InteractionHand> HAND_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockVariantApplyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    HAND_CODEC,                    BlockVariantApplyPacket::hand,
                    ResourceLocation.STREAM_CODEC, BlockVariantApplyPacket::blockId,
                    BlockVariantApplyPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlockVariantApplyPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            ItemStack held = player.getItemInHand(packet.hand());
            if (held.isEmpty()) return;

            Block current = Block.byItem(held.getItem());
            if (!BlockVariantRegistry.isKnownVariant(current)) return;

            Block chosen = BuiltInRegistries.BLOCK.getOptional(packet.blockId()).orElse(null);
            if (chosen == null || !BlockVariantRegistry.isKnownVariant(chosen)) return;

            Item chosenItem = chosen.asItem();
            if (chosenItem == Items.AIR || chosenItem == held.getItem()) return;

            player.setItemInHand(packet.hand(), new ItemStack(chosenItem, held.getCount()));
        });
    }
}
