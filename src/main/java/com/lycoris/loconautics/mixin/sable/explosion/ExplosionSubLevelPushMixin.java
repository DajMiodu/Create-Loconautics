package com.lycoris.loconautics.mixin.sable.explosion;

import com.lycoris.loconautics.content.explosive.ExplosionSubLevelPush;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// remapfalse so sinytra connector cannot rewrite explode and the shadowed fields into fabric intermediary names
@Mixin(value = Explosion.class, remap = false)
public abstract class ExplosionSubLevelPushMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private double x;

    @Shadow
    @Final
    private double y;

    @Shadow
    @Final
    private double z;

    @Shadow
    @Final
    private float radius;

    @Inject(method = "explode", at = @At("TAIL"))
    private void loconautics$pushNearbySubLevels(CallbackInfo ci) {
        if (this.level.isClientSide() || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        ExplosionSubLevelPush.apply(serverLevel, new Vec3(this.x, this.y, this.z), this.radius);
    }
}
