package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.ClientStrands;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.common.strand.StrandRenderer;
import com.lycoris.loconautics.network.packets.attachment.SteelCableBreakPacket;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SteelCableHover {

    private static final double HALF_THICKNESS = 0.25;

    @Nullable
    public static UUID hovered;

    private SteelCableHover() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !isWrench(player.getMainHandItem())) {
            hovered = null;
            return;
        }
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0;
        Vec3 eye = player.getEyePosition();
        Vec3 target = RaycastHelper.getTraceTarget(player, reach, eye);
        HitResult hit = mc.hitResult;
        double bestSqr = hit == null ? Double.MAX_VALUE
                : Sable.HELPER.projectOutOfSubLevel(mc.level, hit.getLocation()).distanceToSqr(eye);
        hovered = raycast(new Vector3d(eye.x, eye.y, eye.z), new Vector3d(target.x, target.y, target.z), bestSqr);
    }

    @Nullable
    private static UUID raycast(Vector3dc from, Vector3dc to, double bestSqr) {
        Vector3d localFrom = new Vector3d();
        Vector3d localTo = new Vector3d();
        Vector3d normal = new Vector3d();
        UUID best = null;
        for (Map.Entry<UUID, ClientStrands.Strand> entry : entries()) {
            var points = entry.getValue().points();
            for (int i = 0; i < points.size() - 1; i++) {
                Vector3d a = points.get(i).position;
                Vector3d b = points.get(i + 1).position;
                double length = a.distance(b);
                if (length < 1.0e-6) {
                    continue;
                }
                b.sub(a, normal).normalize();
                AABB bounds = new AABB(-HALF_THICKNESS, 0.0, -HALF_THICKNESS,
                        HALF_THICKNESS, length, HALF_THICKNESS);
                Quaternionf rot = StrandRenderer.rotationTo(UP, normal);
                rot.transformInverse(localFrom.set(from).sub(a));
                rot.transformInverse(localTo.set(to).sub(a));
                Optional<Vec3> clip = bounds.clip(
                        new Vec3(localFrom.x, localFrom.y, localFrom.z), new Vec3(localTo.x, localTo.y, localTo.z));
                if (clip.isEmpty()) {
                    continue;
                }
                double distSqr = clip.get().distanceToSqr(localFrom.x, localFrom.y, localFrom.z);
                if (distSqr > bestSqr) {
                    continue;
                }
                bestSqr = distSqr;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static final Vector3d UP = new Vector3d(0.0, 1.0, 0.0);

    public static boolean isWrench(ItemStack stack) {
        return AllItems.WRENCH.isIn(stack);
    }

    public static boolean onUse() {
        Minecraft mc = Minecraft.getInstance();
        if (hovered == null || mc.player == null || !isWrench(mc.player.getMainHandItem())) {
            return false;
        }
        CatnipServices.NETWORK.sendToServer(new SteelCableBreakPacket(hovered));
        mc.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static Iterable<Map.Entry<UUID, ClientStrands.Strand>> entries() {
        List<Map.Entry<UUID, ClientStrands.Strand>> all = new ArrayList<>(StrandKind.STEEL_CABLE.clientStrands().entries());
        all.addAll(StrandKind.HOSE_LINK.clientStrands().entries());
        return all;
    }
}
