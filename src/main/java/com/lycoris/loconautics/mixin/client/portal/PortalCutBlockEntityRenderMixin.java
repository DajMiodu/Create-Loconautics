package com.lycoris.loconautics.mixin.client.portal;

import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class PortalCutBlockEntityRenderMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void loconautics$skipCutBlockEntity(E blockEntity, float partialTick,
                                                                        PoseStack poseStack,
                                                                        MultiBufferSource bufferSource,
                                                                        CallbackInfo ci) {
        BlockPos pos = blockEntity.getBlockPos();
        if (PortalCutsClient.cutAtPlot(pos.getX(), pos.getY(), pos.getZ())) {
            ci.cancel();
        }
    }
}
