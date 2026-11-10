package com.lycoris.loconautics.content.boiler;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BoilerBlastEffects {

    private static final int BURST_PARTICLES = 90;
    private static final int STEAM_BURST_PARTICLES = 160;
    private static final int JET_PARTICLES = 140;
    private static final double JET_SPREAD = 0.35;
    private static final float BURN_SECONDS = 8f;
    private static final float BURN_DAMAGE = 6f;
    private static final float REACH_PER_POWER = 2.0f;

    private BoilerBlastEffects() {}

    public static void burst(ServerLevel level, Vec3 center, float power, float pressureFraction) {
        Vec3 at = Sable.HELPER.projectOutOfSubLevel(level, center);
        RandomSource random = level.random;
        for (int i = 0; i < BURST_PARTICLES; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSqr() < 1.0E-6) continue;
            dir = dir.normalize();
            level.sendParticles(pick(random), at.x, at.y, at.z, 0, dir.x, dir.y, dir.z,
                    0.4 + random.nextDouble() * 0.7);
        }
        int steam = Math.round(STEAM_BURST_PARTICLES * pressureFraction);
        for (int i = 0; i < steam; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSqr() < 1.0E-6) continue;
            dir = dir.normalize();
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, dir.x, dir.y, dir.z,
                    0.6 + random.nextDouble() * 1.0);
        }
        scorch(level, at, power);
    }

    public static void jet(ServerLevel level, Vec3 end, Vec3 away, float power) {
        Vec3 at = Sable.HELPER.projectOutOfSubLevel(level, end);
        Vec3 tip = Sable.HELPER.projectOutOfSubLevel(level, end.add(away.lengthSqr() < 1.0E-6
                ? new Vec3(0.0, 1.0, 0.0) : away.normalize()));
        Vec3 axis = tip.subtract(at).normalize();
        RandomSource random = level.random;
        for (int i = 0; i < JET_PARTICLES; i++) {
            Vec3 dir = axis.add(new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                    .scale(JET_SPREAD)).normalize();
            level.sendParticles(pickJet(random), at.x, at.y, at.z, 0, dir.x, dir.y, dir.z,
                    0.8 + random.nextDouble() * 0.9);
        }
        scorch(level, at, power);
    }

    private static ParticleOptions pickJet(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0 -> ParticleTypes.FLAME;
            case 1 -> ParticleTypes.LARGE_SMOKE;
            default -> ParticleTypes.CLOUD;
        };
    }

    private static ParticleOptions pick(RandomSource random) {
        return switch (random.nextInt(3)) {
            case 0 -> ParticleTypes.FLAME;
            case 1 -> ParticleTypes.LARGE_SMOKE;
            default -> ParticleTypes.CLOUD;
        };
    }

    private static void scorch(ServerLevel level, Vec3 at, float power) {
        double reach = power * REACH_PER_POWER;
        AABB box = new AABB(at, at).inflate(reach);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double closeness = 1.0 - entity.position().distanceTo(at) / reach;
            if (closeness <= 0.0) continue;
            entity.setRemainingFireTicks((int) (BURN_SECONDS * 20f));
            entity.hurt(level.damageSources().inFire(), (float) (BURN_DAMAGE * closeness));
        }
    }
}
