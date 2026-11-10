package com.lycoris.loconautics.mixin.client.conductor;

import com.lycoris.loconautics.client.ConductorHats;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlazeBurnerBlockEntity.class)
public abstract class BlazeConductorHeadMixin {

    @Shadow(remap = false)
    protected LerpedFloat headAngle;

    @Shadow(remap = false)
    public LerpedFloat headAnimation;

    @Shadow(remap = false)
    public boolean hat;

    @Shadow(remap = false)
    public abstract boolean isValidBlockAbove();

    @Unique
    private Vector3d loconautics$lastOrigin;

    @Unique
    private boolean loconautics$moving;

    @Unique
    private final Vector3d loconautics$velocity = new Vector3d();

    @Unique
    private static final double VELOCITY_BLEND = 0.15;

    @Unique
    private static final double TRAVEL_ENTER = 0.03;

    @Unique
    private static final double TRAVEL_LEAVE = 0.012;

    @Inject(method = "tickAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$faceTravel(CallbackInfo ci) {
        BlazeBurnerBlockEntity self = (BlazeBurnerBlockEntity) (Object) this;
        if (!(hat || ConductorHats.wearsHat(self)) || isValidBlockAbove()) {
            return;
        }
        Vec3 travel = loconautics$travel(self);
        if (travel == null) {
            return;
        }
        float target = AngleHelper.deg(-Mth.atan2(travel.z, travel.x)) - 90;
        target = headAngle.getValue() + AngleHelper.getShortestAngleDiff(headAngle.getValue(), target);
        headAngle.chase(target, .5f, Chaser.EXP);
        headAngle.tickChaser();
        headAnimation.chase(0, .25f, Chaser.exp(.25f));
        headAnimation.tickChaser();
        ci.cancel();
    }

    @Unique
    private Vec3 loconautics$travel(BlazeBurnerBlockEntity self) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(self);
        if (sub == null) {
            return null;
        }
        BlockPos at = self.getBlockPos();
        Vector3d origin = sub.logicalPose().transformPosition(
                new Vector3d(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5), new Vector3d());
        Vector3d last = loconautics$lastOrigin;
        loconautics$lastOrigin = origin;
        if (last == null) {
            return null;
        }
        loconautics$velocity.mul(1.0 - VELOCITY_BLEND).add(origin.sub(last, new Vector3d()).mul(VELOCITY_BLEND));
        Vector3d local = sub.logicalPose().transformNormalInverse(loconautics$velocity, new Vector3d());
        double flat = local.x * local.x + local.z * local.z;
        if (flat >= TRAVEL_ENTER * TRAVEL_ENTER) {
            loconautics$moving = true;
        } else if (flat < TRAVEL_LEAVE * TRAVEL_LEAVE) {
            loconautics$moving = false;
        }
        return loconautics$moving ? new Vec3(local.x, 0.0, local.z) : null;
    }
}
