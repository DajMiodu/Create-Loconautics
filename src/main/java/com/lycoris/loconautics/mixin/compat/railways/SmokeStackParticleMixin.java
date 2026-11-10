package com.lycoris.loconautics.mixin.compat.railways;

import com.lycoris.loconautics.trainphysics.fx.SubLevelParticleRedirect;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.railwayteam.railways.content.smokestack.SmokeEmissionParams", remap = false)
public abstract class SmokeStackParticleMixin {

    private static final String MAKE_PARTICLES =
            "makeParticles(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;ZDZ"
            + "Lnet/minecraft/world/item/DyeColor;Ljava/lang/Boolean;Z)V";

    @Redirect(method = MAKE_PARTICLES, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle"
                    + "(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
    private void loconautics$alwaysVisibleToWorld(Level level, ParticleOptions options, boolean force,
            double x, double y, double z, double vx, double vy, double vz) {
        SubLevelParticleRedirect.addAlwaysVisibleParticle(level, options, force, x, y, z, vx, vy, vz);
    }

    @Redirect(method = MAKE_PARTICLES, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle"
                    + "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void loconautics$particleToWorld(Level level, ParticleOptions options,
            double x, double y, double z, double vx, double vy, double vz) {
        SubLevelParticleRedirect.addParticle(level, options, x, y, z, vx, vy, vz);
    }
}
