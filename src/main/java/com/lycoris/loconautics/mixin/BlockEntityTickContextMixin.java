package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity", remap = false)
public abstract class BlockEntityTickContextMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void loconautics$enter(CallbackInfo ci) {
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide) {
            TrainAdvancements.blockEntityTicking(blockEntity);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void loconautics$exit(CallbackInfo ci) {
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide) {
            TrainAdvancements.blockEntityTicking(null);
        }
    }
}
