package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.HoseStrandManager;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandDriver;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.lycoris.loconautics.network.PacketTargets;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class StrandShearing {
    private static final double RADIUS = 0.25;

    private record Hit(int kind, UUID id, double distanceSquared) {}

    private StrandShearing() {}

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && cut(player, event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && cut(player, event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean cut(ServerPlayer player, InteractionHand hand) {
        if (player.isSpectator() || !player.mayBuild()
                || player.getCooldowns().isOnCooldown(player.getItemInHand(hand).getItem())
                || !player.getItemInHand(hand).canPerformAction(ItemAbilities.SHEARS_CARVE)) return false;
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 end = eye.add(player.getViewVector(1).scale(reach));
        double limit = reach * reach;
        HitResult obstruction = player.pick(reach, 1, false);
        if (obstruction.getType() != HitResult.Type.MISS) {
            limit = Math.min(limit, Sable.HELPER.projectOutOfSubLevel(level, obstruction.getLocation())
                    .distanceToSqr(eye));
        }
        Hit nearest = null;
        RopeManager<?>[] managers = {SteelCableRopeManager.get(level), HoseRopeManager.get(level)};
        for (int kind = 0; kind < managers.length; kind++) {
            RopeManager<?> manager = managers[kind];
            if (manager == null) continue;
            for (AttachedRopePhysicsObject rope : manager.all()) {
                if (!rope.isActive() || !rope.areAttachmentsLoaded(level)) continue;
                double distance = hitDistanceSquared(eye, end, rope.getPoints());
                if (distance < limit) {
                    limit = distance;
                    nearest = new Hit(kind, rope.getUUID(), distance);
                }
            }
        }
        if (nearest == null || !remove(level, nearest)) return false;
        player.getCooldowns().addCooldown(player.getItemInHand(hand).getItem(), 4);
        if (!player.isCreative()) {
            player.getItemInHand(hand).hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        player.swing(hand, true);
        Vec3 at = eye.add(player.getViewVector(1).scale(Math.sqrt(nearest.distanceSquared())));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1, 1);
        return true;
    }

    private static double hitDistanceSquared(Vec3 from, Vec3 to, List<Vector3d> points) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size(); i++) {
            Vector3d a = points.get(i - 1), b = points.get(i);
            double length = a.distance(b);
            if (!Double.isFinite(length) || length < 1e-6) continue;
            Quaterniond rotation = new Quaterniond().rotationTo(new Vector3d(0, 1, 0),
                    b.sub(a, new Vector3d()).normalize());
            Vector3d localFrom = rotation.transformInverse(new Vector3d(from.x, from.y, from.z).sub(a));
            Vector3d localTo = rotation.transformInverse(new Vector3d(to.x, to.y, to.z).sub(a));
            Vec3 origin = new Vec3(localFrom.x, localFrom.y, localFrom.z);
            AABB bounds = new AABB(-RADIUS, 0, -RADIUS, RADIUS, length, RADIUS);
            if (bounds.contains(origin)) return 0;
            var hit = bounds.clip(origin, new Vec3(localTo.x, localTo.y, localTo.z));
            if (hit.isPresent()) best = Math.min(best, origin.distanceToSqr(hit.get()));
        }
        return best;
    }

    private static boolean remove(ServerLevel level, Hit hit) {
        // delegate to the normal break paths they remove physics, saved data, and both endpoint bindings.
        if (hit.kind() == 0) {
            var strands = SteelCableStrandManager.get(level);
            var ropes = SteelCableRopeManager.get(level);
            var strand = strands == null ? null : strands.get(hit.id());
            if (strand == null || ropes == null) return false;
            SteelCableStrandDriver.breakStrand(level, strands, ropes, strand, null);
            return true;
        }
        if (hit.kind() == 1) {
            var strands = HoseStrandManager.get(level);
            var strand = strands == null ? null : strands.get(hit.id());
            if (strand == null) return false;
            var winch = PacketTargets.blockEntity(level, strand.winchPos, HoseWinchBlockEntity.class);
            if (winch == null || winch.getAttachedStrand() != strand) return false;
            winch.onWinchBroken();
            return strands.get(hit.id()) == null;
        }
        return false;
    }
}
