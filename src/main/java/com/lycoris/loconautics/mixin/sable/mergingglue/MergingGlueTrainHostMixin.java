package com.lycoris.loconautics.mixin.sable.mergingglue;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.spawn.GlueHost;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MergingGlueBlockEntity.class, remap = false)
public abstract class MergingGlueTrainHostMixin {

    @Shadow
    private int ageTicks;
    @Shadow
    private boolean isController;
    @Shadow
    private Rotation endRotation;
    @Shadow
    private BlockPos partnerPosition;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void loconautics$pieceGoesIntoTrain(CallbackInfo ci) {
        MergingGlueBlockEntity self = (MergingGlueBlockEntity) (Object) this;
        if (!isController || ageTicks <= 12 || partnerPosition == null
                || !(self.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MergingGlueBlockEntity partner = self.getPartnerGlue();
        if (partner == null
                || !(Sable.HELPER.getContaining((BlockEntity) self) instanceof ServerSubLevel own)
                || !(Sable.HELPER.getContaining(level, partnerPosition) instanceof ServerSubLevel other)) {
            return;
        }
        ServerSubLevel train = GlueHost.trainOf(own, other);
        if (train == null) {
            return;
        }
        ci.cancel();
        SubLevelContacts.enable(own.getUniqueId(), other.getUniqueId());
        LoconauticsConstants.LOGGER.info("[gluemerge] piece {} goes into train body {}",
                (train == own ? other : own).getUniqueId(), train.getUniqueId());
        ((MergingGlueAccessor) self).loconautics$breakGlue();
        if (train == own) {
            ((MergingGlueAccessor) partner).loconautics$disassembleToPartner(GlueHost.inverse(endRotation));
        } else {
            ((MergingGlueAccessor) self).loconautics$disassembleToPartner(endRotation);
        }
    }
}
