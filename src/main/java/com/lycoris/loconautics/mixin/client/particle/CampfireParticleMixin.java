package com.lycoris.loconautics.mixin.client.particle;

import com.lycoris.loconautics.trainphysics.fx.SubLevelParticleRedirect;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CampfireBlock.class, remap = false)
public abstract class CampfireParticleMixin {

    private static final String MAKE_PARTICLES =
            "makeParticles(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZZ)V";
    private static final String ANIMATE_TICK =
            "animateTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V";

    @Redirect(method = MAKE_PARTICLES, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle"
                    + "(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
    private static void loconautics$smokeToWorld(Level level, ParticleOptions options, boolean force,
            double x, double y, double z, double vx, double vy, double vz) {
        SubLevelParticleRedirect.addAlwaysVisibleParticle(level, options, force, x, y, z, vx, vy, vz);
    }

    @Redirect(method = MAKE_PARTICLES, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle"
                    + "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private static void loconautics$extraSmokeToWorld(Level level, ParticleOptions options,
            double x, double y, double z, double vx, double vy, double vz) {
        SubLevelParticleRedirect.addParticle(level, options, x, y, z, vx, vy, vz);
    }

    @Redirect(method = ANIMATE_TICK, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle"
                    + "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void loconautics$sparksToWorld(Level level, ParticleOptions options,
            double x, double y, double z, double vx, double vy, double vz) {
        SubLevelParticleRedirect.addParticle(level, options, x, y, z, vx, vy, vz);
    }
}
