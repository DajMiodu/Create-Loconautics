package com.lycoris.loconautics.mixin.client.particle;

import com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock;
import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Particle.class)
public abstract class ParticleWorldLockMixin implements ParticleSubLevelKickable {

    @Unique
    private boolean loconautics$worldLocked;

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDD)V", at = @At("RETURN"))
    private void loconautics$captureWorldLock(ClientLevel level, double x, double y, double z, CallbackInfo ci) {
        this.loconautics$worldLocked = ParticleWorldLock.active();
    }

    @Override
    public boolean sable$shouldCareAboutIntersectingSubLevels() {
        return !this.loconautics$worldLocked;
    }

    @Override
    public boolean sable$shouldKickFromTracking() {
        return true;
    }

    @Override
    public boolean sable$shouldCollideWithTrackingSubLevel() {
        return true;
    }
}
