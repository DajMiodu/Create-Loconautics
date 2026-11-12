package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.client.StrandPreviewOutline;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SteelCablePreview {

    private SteelCablePreview() {
    }

    public static void tickPreview() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.screen != null) {
            return;
        }
        BlockPos first = pendingFirst(player);
        if (first == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        SteelCableItem item = heldItem(player);
        SteelCableHolder firstHolder = SteelCableItem.getHolder(level, first);
        Vec3 firstPoint = Sable.HELPER.projectOutOfSubLevel(level,
                firstHolder != null ? firstHolder.getAttachmentPoint() : Vec3.atCenterOf(first));
        StrandPreviewOutline.draw(level, item.cableType().carriesFluid() ? "HoseLink" : "SteelCable", firstPoint,
                hit.getLocation(), hit.getBlockPos(), item.maxRange(),
                item.isValidAttachment(level, hit.getBlockPos()) && !hit.getBlockPos().equals(first));
    }

    @Nullable
    private static BlockPos pendingFirst(LocalPlayer player) {
        BlockPos held = heldFirst(player.getMainHandItem());
        return held != null ? held : heldFirst(player.getOffhandItem());
    }

    private static SteelCableItem heldItem(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        return (SteelCableItem) (heldFirst(main) != null ? main : player.getOffhandItem()).getItem();
    }

    @Nullable
    private static BlockPos heldFirst(ItemStack stack) {
        return stack.getItem() instanceof SteelCableItem ? stack.get(SteelCableItem.firstConnection()) : null;
    }
}
