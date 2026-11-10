package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.rail.RailPick;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SableTrainRelocatePacket(UUID subLevelId, RailPick pick, Vec3 lookAngle)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SableTrainRelocatePacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("sable_train_relocate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SableTrainRelocatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.subLevelId());
                        RailPick.STREAM_CODEC.encode(buf, p.pick());
                        buf.writeDouble(p.lookAngle().x);
                        buf.writeDouble(p.lookAngle().y);
                        buf.writeDouble(p.lookAngle().z);
                    },
                    buf -> new SableTrainRelocatePacket(
                            buf.readUUID(),
                            RailPick.STREAM_CODEC.decode(buf),
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()))
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SableTrainRelocatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            SableTrainSpawner.relocate(player, packet.subLevelId(), packet.pick(), packet.lookAngle());
        });
    }
}
