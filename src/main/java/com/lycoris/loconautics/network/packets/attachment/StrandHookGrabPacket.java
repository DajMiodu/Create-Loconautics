package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.screwlink.ScrewLinkHook;
import com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRope;
import com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRopeManager;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.UUID;

public record StrandHookGrabPacket(UUID strandUUID, boolean stop, float range, boolean rotating,
                                   Quaterniond orientation) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrandHookGrabPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("strand_hook_grab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrandHookGrabPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.strandUUID());
                        buf.writeBoolean(p.stop());
                        buf.writeFloat(p.range());
                        buf.writeBoolean(p.rotating());
                        buf.writeDouble(p.orientation().x).writeDouble(p.orientation().y)
                                .writeDouble(p.orientation().z).writeDouble(p.orientation().w);
                    },
                    buf -> new StrandHookGrabPacket(buf.readUUID(), buf.readBoolean(), buf.readFloat(),
                            buf.readBoolean(),
                            new Quaterniond(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble())));

    public static StrandHookGrabPacket stopping(UUID strandUUID) {
        return new StrandHookGrabPacket(strandUUID, true, 0.0f, false, new Quaterniond());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StrandHookGrabPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!PacketTargets.finite(packet.range())
                    || !PacketTargets.finite((float) packet.orientation().lengthSquared())) {
                return;
            }
            ScrewLinkHook hook = hook(level, packet.strandUUID());
            if (hook == null) {
                return;
            }
            if (packet.stop()) {
                hook.stopGrab(player.getUUID());
                return;
            }
            hook.updateGrab(player.getUUID(), packet.range(), packet.rotating(), packet.orientation());
        });
    }

    @Nullable
    private static ScrewLinkHook hook(ServerLevel level, UUID strandId) {
        ScrewLinkRopeManager ropes = ScrewLinkRopeManager.get(level);
        if (ropes == null) {
            return null;
        }
        ScrewLinkRope rope = ropes.get(strandId);
        return rope == null ? null : rope.getHook();
    }
}
