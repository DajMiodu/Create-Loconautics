package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TrainRunOver {

    private static final ResourceKey<DamageType> RUN_OVER =
            ResourceKey.create(Registries.DAMAGE_TYPE, LoconauticsConstants.id("run_over"));
    private static final double MIN_SPEED = 0.35;
    private static final double REACH = 0.3;
    private static final float RARE_MESSAGE_CHANCE = 0.05f;
    private static final String[] RARE_MESSAGES = {
            "death.attack.loconautics.run_over.right_of_way",
            "death.attack.loconautics.run_over.beneath",
            "death.attack.loconautics.run_over.jaywalking",
            "death.attack.loconautics.run_over.fined"
    };

    private TrainRunOver() {
    }

    private static final class RareMessage extends DamageSource {
        private final String key;
        private final String trainName;

        private RareMessage(Holder<DamageType> type, String key, String trainName) {
            super(type);
            this.key = key;
            this.trainName = trainName;
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity victim) {
            return Component.translatable(key, victim.getDisplayName(), trainName);
        }
    }

    static void tick(SableTrain train) {
        if (!AllConfigs.server().trains.trainsCauseDamage.get()) {
            return;
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
        RigidBodyHandle handle = sub == null || sub.isRemoved() ? null : RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        Vec3 motion = new Vec3(velocity.x, velocity.y, velocity.z).scale(1.0 / 20.0);
        if (motion.length() <= MIN_SPEED) {
            return;
        }
        List<ServerSubLevel> bodies = new ArrayList<>();
        bodies.add(sub);
        for (UUID wheel : GhostWheelBodies.wheelBodiesOf(sub.getUniqueId())) {
            ServerSubLevel wheelSub = SubLevelData.resolve(train.level(), wheel);
            if (wheelSub != null && !wheelSub.isRemoved()) {
                bodies.add(wheelSub);
            }
        }
        List<AABB> boxes = new ArrayList<>(bodies.size());
        AABB reach = null;
        for (ServerSubLevel body : bodies) {
            BoundingBox3dc bounds = body.boundingBox();
            if (bounds == null) {
                boxes.add(null);
                continue;
            }
            AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()).inflate(REACH);
            boxes.add(box);
            reach = reach == null ? box : reach.minmax(box);
        }
        if (reach == null) {
            return;
        }
        List<LivingEntity> nearby = train.level().getEntitiesOfClass(LivingEntity.class, reach,
                entity -> canBeHit(entity, motion));
        if (nearby.isEmpty()) {
            return;
        }
        Set<LivingEntity> struck = new HashSet<>();
        for (LivingEntity victim : nearby) {
            for (int i = 0; i < bodies.size(); i++) {
                AABB box = boxes.get(i);
                if (box != null && box.intersects(victim.getBoundingBox()) && touchesBody(bodies.get(i), victim)) {
                    struck.add(victim);
                    break;
                }
            }
        }
        for (LivingEntity victim : struck) {
            hit(train, victim, motion);
        }
    }

    private static boolean canBeHit(LivingEntity entity, Vec3 motion) {
        if (!entity.isAlive() || !entity.onGround() || entity.isPassenger()
                || entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        SubLevel riding = Sable.HELPER.getTrackingSubLevel(entity);
        if (riding != null && (SableTrainRegistry.bySubLevel(riding.getUniqueId()) != null
                || GhostWheelBodies.isWheelBody(riding.getUniqueId()))) {
            return false;
        }
        return motion.subtract(entity.getDeltaMovement()).length() > MIN_SPEED;
    }

    private static boolean touchesBody(ServerSubLevel sub, LivingEntity victim) {
        AABB box = victim.getBoundingBox().inflate(REACH);
        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY);
        Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY);
        Vector3d corner = new Vector3d();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ);
            sub.logicalPose().transformPositionInverse(corner, corner);
            min.min(corner);
            max.max(corner);
        }
        ServerLevel plot = sub.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(min.x, min.y, min.z),
                BlockPos.containing(max.x, max.y, max.z))) {
            if (!plot.getBlockState(pos).getCollisionShape(plot, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void hit(SableTrain train, LivingEntity victim, Vec3 motion) {
        Vec3 relative = motion.subtract(victim.getDeltaMovement());
        double damage = relative.length();
        if (victim.getType().getCategory() == MobCategory.MONSTER) {
            damage *= 2;
        }
        ServerLevel level = train.level();
        DamageSource source = level.damageSources().source(RUN_OVER);
        if (victim instanceof Player && !train.name().isEmpty() && level.random.nextFloat() < RARE_MESSAGE_CHANCE) {
            source = new RareMessage(source.typeHolder(), RARE_MESSAGES[level.random.nextInt(RARE_MESSAGES.length)],
                    train.name());
        }
        if (!victim.hurt(source, (int) (damage * 16))) {
            return;
        }
        level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1, .75f);
        Vec3 flung = victim.getDeltaMovement()
                .add(motion.multiply(1, 0, 1).normalize().add(0, .25, 0).scale(damage * 4))
                .add(relative);
        victim.setDeltaMovement(VecHelper.clamp(flung, 3));
        victim.hurtMarked = true;
        TrainAdvancements.ranOver(train, !victim.isAlive());
    }
}
