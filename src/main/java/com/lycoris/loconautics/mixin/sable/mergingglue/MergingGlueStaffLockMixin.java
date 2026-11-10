package com.lycoris.loconautics.mixin.sable.mergingglue;

import com.lycoris.loconautics.trainphysics.spawn.GlueHost;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MergingGlueBlockEntity.class, remap = false)
public abstract class MergingGlueStaffLockMixin {

    @Unique
    private Quaterniond loconautics$partnerOrientation;

    @Inject(method = "startControlling", at = @At("HEAD"))
    private void loconautics$freeStaffLocks(MergingGlueBlockEntity partner, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) {
            return;
        }
        SubLevel selfSub = Sable.HELPER.getContaining(self);
        SubLevel partnerSub = Sable.HELPER.getContaining((BlockEntity) partner);
        loconautics$partnerOrientation = partnerSub == null ? null
                : new Quaterniond(partnerSub.logicalPose().orientation());
        SubLevelMergeWatcher.releaseStaffLock(level, selfSub);
        SubLevelMergeWatcher.releaseStaffLock(level, partnerSub);
        if (selfSub instanceof ServerSubLevel controllerBody && partnerSub instanceof ServerSubLevel partnerBody) {
            boolean partnerGoes = GlueHost.secondGoes(controllerBody, partnerBody);
            SubLevelMergeWatcher.beginPull(level, (partnerGoes ? partnerBody : controllerBody).getUniqueId(),
                    (partnerGoes ? controllerBody : partnerBody).getUniqueId());
        }
    }

    @Inject(method = "startControlling", at = @At("TAIL"))
    private void loconautics$restorePartnerPose(MergingGlueBlockEntity partner, CallbackInfo ci) {
        Quaterniond saved = loconautics$partnerOrientation;
        loconautics$partnerOrientation = null;
        SubLevel partnerSub = Sable.HELPER.getContaining((BlockEntity) partner);
        if (saved == null || partnerSub == null) {
            return;
        }
        Quaterniond written = partnerSub.logicalPose().orientation();
        written.set(saved);
    }
}
