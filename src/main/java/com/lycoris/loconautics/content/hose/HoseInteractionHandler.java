package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.client.StrandPreviewOutline;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableItem;
import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import com.lycoris.loconautics.network.packets.attachment.HoseAttachPacket;
import com.lycoris.loconautics.network.packets.attachment.HoseLinkPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HoseInteractionHandler {

    @Nullable private static BlockPos pendingWinchPos;
    @Nullable private static UUID pendingNozzleSub;
    @Nullable private static BlockPos pendingNozzleLocal;

    private HoseInteractionHandler() {
    }

    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator()) {
            return;
        }
        if (!(player.getItemInHand(event.getHand()).getItem() instanceof HoseItem)) {
            return;
        }
        if (player.isShiftKeyDown() && hasPending()) {
            clearPending();
            player.displayClientMessage(Component.translatable("message.loconautics.hose.cleared"), true);
            event.setCanceled(true);
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos hitPos = hit.getBlockPos();
        SubLevel sub = Sable.HELPER.getContaining(mc.level, hitPos);
        UUID hitSub = sub == null ? null : sub.getUniqueId();
        Kind kind = classify(mc.level, hitPos);
        ItemStack stack = player.getItemInHand(event.getHand());
        BlockPos firstConnection = stack.get(SteelCableItem.firstConnection());

        if (firstConnection != null) {
            if (kind == Kind.WINCH) {
                SubLevel firstSub = Sable.HELPER.getContaining(mc.level, firstConnection);
                fire(player, hitPos, firstSub == null ? null : firstSub.getUniqueId(), firstConnection);
                stack.remove(SteelCableItem.firstConnection());
                event.setCanceled(true);
            }
            return;
        }

        if (pendingWinchPos != null) {
            if (kind == Kind.NOZZLE || kind == Kind.LINK) {
                fire(player, pendingWinchPos, hitSub, hitPos);
                event.setCanceled(true);
            }
            return;
        }
        if (pendingNozzleLocal != null) {
            if (kind == Kind.WINCH) {
                fire(player, hitPos, pendingNozzleSub, pendingNozzleLocal);
                event.setCanceled(true);
            } else if (kind == Kind.LINK) {
                PacketDistributor.sendToServer(new HoseLinkPacket(pendingNozzleLocal, hitPos));
                clearPending();
                player.displayClientMessage(Component.translatable("message.loconautics.hose.attached"), true);
                event.setCanceled(true);
            }
            return;
        }

        if (kind == Kind.NOZZLE) {
            pendingNozzleSub = hitSub;
            pendingNozzleLocal = hitPos;
            player.displayClientMessage(Component.translatable("message.loconautics.hose.nozzle_selected"), true);
            event.setCanceled(true);
        } else if (kind == Kind.WINCH) {
            pendingWinchPos = hitPos;
            player.displayClientMessage(Component.translatable("message.loconautics.hose.winch_selected"), true);
            event.setCanceled(true);
        }
    }

    private enum Kind { WINCH, NOZZLE, LINK, NONE }

    private static Kind classify(Level level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        BlockEntity be = (sub != null ? sub.getLevel() : level).getBlockEntity(pos);
        if (be instanceof HoseWinchBlockEntity) {
            return Kind.WINCH;
        }
        if (be instanceof HoseNozzleBlockEntity && sub != null) {
            return Kind.NOZZLE;
        }
        if (be instanceof SteelCableHolder holder && holder.acceptsFluidLinks() && !(be instanceof HoseNozzleBlockEntity)) {
            return Kind.LINK;
        }
        return Kind.NONE;
    }

    private static void fire(LocalPlayer player, BlockPos winchPos, @Nullable UUID farSub, BlockPos farLocal) {
        PacketDistributor.sendToServer(new HoseAttachPacket(winchPos, farSub, farLocal));
        clearPending();
        player.displayClientMessage(Component.translatable("message.loconautics.hose.attached"), true);
    }

    private static boolean hasPending() {
        return pendingWinchPos != null || (pendingNozzleSub != null && pendingNozzleLocal != null);
    }

    private static void clearPending() {
        pendingWinchPos = null;
        pendingNozzleSub = null;
        pendingNozzleLocal = null;
    }

    public static void tickPreview() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.screen != null || !hasPending()) {
            return;
        }
        boolean holding = player.getMainHandItem().getItem() instanceof HoseItem
                || player.getOffhandItem().getItem() instanceof HoseItem;
        if (!holding) {
            return;
        }
        Vec3 firstPoint = resolveFirstPoint(level);
        if (firstPoint == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        StrandPreviewOutline.draw(level, "Hose", firstPoint, hit.getLocation(), hit.getBlockPos(),
                HoseType.DEFAULT.maxLength(), targetComplementary(level, hit.getBlockPos()));
    }

    @Nullable
    private static Vec3 resolveFirstPoint(ClientLevel level) {
        if (pendingWinchPos != null) {
            return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pendingWinchPos));
        }
        if (pendingNozzleSub == null || pendingNozzleLocal == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !(container.getSubLevel(pendingNozzleSub) instanceof ClientSubLevel sub)) {
            return null;
        }
        Pose3dc pose = sub.renderPose();
        Vector3d w = pose.transformPosition(new Vector3d(
                pendingNozzleLocal.getX() + 0.5, pendingNozzleLocal.getY() + 0.5, pendingNozzleLocal.getZ() + 0.5),
                new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    private static boolean targetComplementary(ClientLevel level, BlockPos hitPos) {
        Kind kind = classify(level, hitPos);
        if (pendingWinchPos != null) {
            return kind == Kind.NOZZLE || kind == Kind.LINK;
        }
        return kind == Kind.WINCH || kind == Kind.LINK;
    }
}
