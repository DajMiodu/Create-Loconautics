package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.network.PacketTargets;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaterniond;

import java.util.UUID;

public record GrabUndockPacket(UUID subLevelUUID, BlockPos nozzleLocalPos, float range) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GrabUndockPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("grab_undock"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrabUndockPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.subLevelUUID());
                        buf.writeBlockPos(p.nozzleLocalPos());
                        buf.writeFloat(p.range());
                    },
                    buf -> new GrabUndockPacket(buf.readUUID(), buf.readBlockPos(), buf.readFloat()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GrabUndockPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!PacketTargets.finite(packet.range())) {
                return;
            }
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null
                    || !(container.getSubLevel(packet.subLevelUUID()) instanceof ServerSubLevel sub)
                    || !(sub.getLevel().getBlockEntity(packet.nozzleLocalPos()) instanceof HoseNozzleBlockEntity nozzle)) {
                return;
            }
            if (!nozzle.canUndock()) {
                return;
            }
            nozzle.undock(level.getGameTime());
            nozzle.updateGrab(player.getUUID(), packet.range(), false, new Quaterniond());
        });
    }
}
