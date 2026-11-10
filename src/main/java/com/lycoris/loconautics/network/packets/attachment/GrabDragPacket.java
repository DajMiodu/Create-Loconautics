package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.grab.GrabbableActor;
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

public record GrabDragPacket(UUID subLevelUUID, BlockPos nozzleLocalPos, float range,
                             boolean rotating, Quaterniond orientation) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GrabDragPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("grab_drag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrabDragPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.subLevelUUID());
                        buf.writeBlockPos(p.nozzleLocalPos());
                        buf.writeFloat(p.range());
                        buf.writeBoolean(p.rotating());
                        buf.writeDouble(p.orientation().x).writeDouble(p.orientation().y)
                                .writeDouble(p.orientation().z).writeDouble(p.orientation().w);
                    },
                    buf -> new GrabDragPacket(buf.readUUID(), buf.readBlockPos(), buf.readFloat(), buf.readBoolean(),
                            new Quaterniond(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble())));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GrabDragPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!PacketTargets.finite(packet.range()) || !PacketTargets.finite((float) packet.orientation().lengthSquared())) {
                return;
            }
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null
                    || !(container.getSubLevel(packet.subLevelUUID()) instanceof ServerSubLevel sub)
                    || !(sub.getLevel().getBlockEntity(packet.nozzleLocalPos()) instanceof GrabbableActor grabbable)
                    || !grabbable.canGrab()) {
                return;
            }
            grabbable.updateGrab(player.getUUID(), packet.range(), packet.rotating(), packet.orientation());
        });
    }
}
