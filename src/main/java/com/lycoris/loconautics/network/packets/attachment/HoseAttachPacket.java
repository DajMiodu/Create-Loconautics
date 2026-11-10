package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.StrandEndpoints;
import com.lycoris.loconautics.content.hose.HoseItem;
import com.lycoris.loconautics.content.hose.HoseStrand;
import com.lycoris.loconautics.content.hose.HoseStrandManager;
import com.lycoris.loconautics.content.hose.HoseType;
import com.lycoris.loconautics.content.hose.link.HoseLinks;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import com.lycoris.loconautics.content.steelcable.SteelCableItem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

public record HoseAttachPacket(BlockPos winchPos, @Nullable UUID farSubLevelId, BlockPos farLocalPos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HoseAttachPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("hose_attach"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HoseAttachPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.winchPos());
                        buf.writeBoolean(p.farSubLevelId() != null);
                        if (p.farSubLevelId() != null) {
                            buf.writeUUID(p.farSubLevelId());
                        }
                        buf.writeBlockPos(p.farLocalPos());
                    },
                    buf -> {
                        BlockPos winch = buf.readBlockPos();
                        UUID sub = buf.readBoolean() ? buf.readUUID() : null;
                        return new HoseAttachPacket(winch, sub, buf.readBlockPos());
                    });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HoseAttachPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (Sable.HELPER.distanceSquaredWithSubLevels(level, player.getEyePosition(),
                    Vec3.atCenterOf(packet.winchPos())) > 64.0) {
                return;
            }
            if (!(level.getBlockEntity(packet.winchPos()) instanceof HoseWinchBlockEntity winch)
                    || !winch.canAttachStrand()) {
                return;
            }
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            BlockEntity far = StrandEndpoints.blockEntity(level, container, packet.farSubLevelId(),
                    packet.farLocalPos());
            if (!canTakeWinchHose(far, packet.farSubLevelId())) {
                return;
            }
            Vector3d farPoint = StrandEndpoints.worldPoint(level, container, packet.farSubLevelId(),
                    packet.farLocalPos());
            if (farPoint == null) {
                return;
            }
            double span = winch.spanTo(
                    new net.minecraft.world.phys.Vec3(farPoint.x, farPoint.y, farPoint.z));
            if (span > HoseType.DEFAULT.maxLength()) {
                return;
            }

            HoseStrand strand = new HoseStrand(UUID.randomUUID(), packet.winchPos(), HoseType.DEFAULT);
            strand.nozzleSubLevelId = packet.farSubLevelId();
            strand.nozzleLocalPos = packet.farLocalPos();
            strand.nozzlePos = BlockPos.containing(farPoint.x, farPoint.y, farPoint.z);
            strand.currentLength = (float) span;

            HoseStrandManager.get(level).create(strand);
            winch.onStrandAttached(strand);
            if (far instanceof HoseNozzleBlockEntity nozzle) {
                nozzle.onStrandAttached(strand);
            }
            level.playSound(null, packet.winchPos(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
            level.playSound(null, BlockPos.containing(farPoint.x, farPoint.y, farPoint.z), SoundEvents.CHAIN_PLACE,
                    SoundSource.BLOCKS, 0.5f, 1.0f);
            clearFirstConnection(player);
        });
    }

    private static boolean canTakeWinchHose(@Nullable BlockEntity far, @Nullable UUID farSub) {
        if (far instanceof HoseNozzleBlockEntity nozzle) {
            return farSub != null && nozzle.canAttachStrand();
        }
        return far instanceof SteelCableHolder holder && holder.acceptsFluidLinks() && !HoseLinks.atLinkLimit(far);
    }

    private static void clearFirstConnection(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof HoseItem) {
                stack.remove(SteelCableItem.firstConnection());
            }
        }
    }
}
