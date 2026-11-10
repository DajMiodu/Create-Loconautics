package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.relocator.RelocatorItemData;
import com.lycoris.loconautics.trainphysics.rail.RailPick;
import com.lycoris.loconautics.trainphysics.spawn.TrainStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record PlaceStoredTrainPacket(RailPick pick, Vec3 lookAngle, boolean offHand)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlaceStoredTrainPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("place_stored_train"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceStoredTrainPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        RailPick.STREAM_CODEC.encode(buf, p.pick());
                        buf.writeDouble(p.lookAngle().x);
                        buf.writeDouble(p.lookAngle().y);
                        buf.writeDouble(p.lookAngle().z);
                        buf.writeBoolean(p.offHand());
                    },
                    buf -> new PlaceStoredTrainPacket(
                            RailPick.STREAM_CODEC.decode(buf),
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlaceStoredTrainPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = player.getItemInHand(packet.offHand() ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND);
            CompoundTag data = RelocatorItemData.read(stack);
            if (data == null || !RelocatorItemData.isStored(data)) {
                return;
            }
            if (!player.canInteractWithBlock(packet.pick().pos(), 24.0)) {
                return;
            }
            List<CompoundTag> structures = RelocatorItemData.structures(data);
            TrainStructures.Placement result = TrainStructures.placeAll(player.serverLevel(), structures,
                    packet.pick(), packet.lookAngle(), player.getUUID(), RelocatorItemData.name(data));
            if (!result.success()) {
                if (!TrainStructures.TRAIN_IN_THE_WAY.equals(result.failure())) {
                    player.sendSystemMessage(Component.literal("[sabletrain] " + result.failure()));
                }
                return;
            }
            stack.shrink(1);
        });
    }
}
