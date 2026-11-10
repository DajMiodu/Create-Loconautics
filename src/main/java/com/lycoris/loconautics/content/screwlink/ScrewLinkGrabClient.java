package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.ClientStrands;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ScrewLinkGrabClient {

    private static final double HOOK_HALF_EXTENT = 0.25;

    private ScrewLinkGrabClient() {
    }

    @Nullable
    public static UUID pickHook() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 eye = player.getEyePosition();
        Vec3 target = RaycastHelper.getTraceTarget(player, reach, eye);
        HitResult hit = mc.hitResult;
        double bestSqr = hit == null ? reach * reach
                : Sable.HELPER.projectOutOfSubLevel(mc.level, hit.getLocation()).distanceToSqr(eye);

        UUID best = null;
        for (Map.Entry<UUID, ClientStrands.Strand> entry : StrandKind.SCREW_LINK.clientStrands().entries()) {
            List<ClientStrands.Point> points = entry.getValue().points();
            if (points.size() < 2) {
                continue;
            }
            Vector3d end = points.get(points.size() - 1).position;
            AABB box = new AABB(end.x - HOOK_HALF_EXTENT, end.y - HOOK_HALF_EXTENT, end.z - HOOK_HALF_EXTENT,
                    end.x + HOOK_HALF_EXTENT, end.y + HOOK_HALF_EXTENT, end.z + HOOK_HALF_EXTENT);
            Optional<Vec3> clip = box.clip(eye, target);
            if (clip.isEmpty()) {
                continue;
            }
            double distSqr = clip.get().distanceToSqr(eye);
            if (distSqr > bestSqr) {
                continue;
            }
            bestSqr = distSqr;
            best = entry.getKey();
        }
        return best;
    }

    @Nullable
    public static Vec3 hookPosition(UUID strandId, float partialTick) {
        ClientStrands.Strand strand = StrandKind.SCREW_LINK.clientStrands().get(strandId);
        if (strand == null || strand.size() == 0) {
            return null;
        }
        Vector3d end = strand.points().get(strand.size() - 1).renderPos(partialTick, new Vector3d());
        return new Vec3(end.x, end.y, end.z);
    }

    public static boolean stillLoaded(UUID strandId) {
        return StrandKind.SCREW_LINK.clientStrands().get(strandId) != null;
    }
}
