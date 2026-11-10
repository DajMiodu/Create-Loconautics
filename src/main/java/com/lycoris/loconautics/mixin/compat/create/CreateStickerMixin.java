package com.lycoris.loconautics.mixin.compat.create;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.compat.create.CreateStickerExtension;
import com.lycoris.loconautics.trainphysics.drive.WeldedBodies;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.contraptions.chassis.StickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

@Mixin(value = StickerBlockEntity.class, remap = false)
public abstract class CreateStickerMixin extends SmartBlockEntity implements CreateStickerExtension {

    private CreateStickerMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    private BlockPos sable$attachedPos;

    @Shadow
    private Quaterniond sable$constraintOrientation;

    @Shadow
    private Vector3d sable$constraintPos1;

    @Shadow
    private Vector3d sable$constraintPos2;

    @Unique
    private boolean loconautics$guardLogged;

    // rapier panics on a joint to a body it does not have
    @Inject(method = "sable$applyConstraint", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$guardBodies(ServerSubLevel thisSubLevel, ServerSubLevel otherSubLevel, CallbackInfo ci) {
        String problem = loconautics$bodyProblem(thisSubLevel);
        if (problem == null) {
            problem = loconautics$bodyProblem(otherSubLevel);
        }
        if (problem == null) {
            loconautics$guardLogged = false;
            boolean trainPair = loconautics$isTrain(thisSubLevel) && loconautics$isTrain(otherSubLevel);
            if (trainPair && !WeldedBodies.pairSettled(thisSubLevel.getUniqueId(), otherSubLevel.getUniqueId())
                    && loconautics$settleWait++ < SETTLE_WAIT_TICKS) {
                ci.cancel();
                return;
            }
            loconautics$settleWait = 0;
            return;
        }
        ci.cancel();
        if (!loconautics$guardLogged) {
            loconautics$guardLogged = true;
            LoconauticsConstants.LOGGER.warn("[stickerguard] skipped joint at {}, {}, retrying next tick", getBlockPos(), problem);
        }
    }

    @Unique
    private static final int SETTLE_WAIT_TICKS = 40;

    @Unique
    private int loconautics$settleWait;

    @Unique
    @Nullable
    private static String loconautics$bodyProblem(@Nullable ServerSubLevel sub) {
        if (sub == null) {
            return null;
        }
        if (sub.isRemoved()) {
            return "body " + sub.getUniqueId() + " is removed";
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null || !handle.isValid()) {
            return "body " + sub.getUniqueId() + " is not in the physics pipeline";
        }
        return null;
    }

    // sable straightens the pair, cars on a curve cannot turn
    @Inject(method = "sable$applyConstraint", at = @At("HEAD"), remap = false)
    private void loconautics$repairNanFrame(ServerSubLevel thisSubLevel, ServerSubLevel otherSubLevel,
                                            CallbackInfo ci) {
        boolean finite = sable$constraintOrientation != null && sable$constraintOrientation.isFinite();
        boolean trainPair = loconautics$isTrain(thisSubLevel) || loconautics$isTrain(otherSubLevel);
        if (finite && !trainPair) {
            return;
        }
        Quaterniond first = thisSubLevel == null ? new Quaterniond() : new Quaterniond(thisSubLevel.logicalPose().orientation());
        Quaterniond second = otherSubLevel == null ? new Quaterniond() : new Quaterniond(otherSubLevel.logicalPose().orientation());
        Quaterniond current = first.conjugate().mul(second).normalize();
        if (!finite) {
            LoconauticsConstants.LOGGER.warn("[sticker] sable built a non finite joint frame between {} and {}, repaired to {}",
                    thisSubLevel == null ? "world" : thisSubLevel.getUniqueId(),
                    otherSubLevel == null ? "world" : otherSubLevel.getUniqueId(), current);
        }
        sable$constraintOrientation = current;
        if (trainPair && thisSubLevel != null && otherSubLevel != null && sable$constraintPos1 != null) {
            Vector3d contact = thisSubLevel.logicalPose().transformPosition(new Vector3d(sable$constraintPos1), new Vector3d());
            sable$constraintPos2 = otherSubLevel.logicalPose().transformPositionInverse(contact, new Vector3d());
        }
    }

    @Unique
    private static boolean loconautics$isTrain(@Nullable ServerSubLevel sub) {
        return sub != null && SableTrainRegistry.bySubLevel(sub.getUniqueId()) != null;
    }

    @Override
    @Unique
    @Nullable
    public UUID loconautics$getPartnerSubLevelId() {
        if (sable$attachedPos == null) {
            return null;
        }
        Level l = this.level;
        if (!(l instanceof ServerLevel serverLevel)) {
            return null;
        }
        ActiveSableCompanion helper = Sable.HELPER;
        ServerSubLevel other = (ServerSubLevel) helper.getContaining(serverLevel, sable$attachedPos);
        return other != null ? other.getUniqueId() : null;
    }
}