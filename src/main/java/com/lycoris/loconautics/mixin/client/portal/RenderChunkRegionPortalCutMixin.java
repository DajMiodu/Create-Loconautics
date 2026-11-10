package com.lycoris.loconautics.mixin.client.portal;

import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionPortalCutMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void loconautics$cutAtPortal(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (PortalCutsClient.isEmpty()) {
            return;
        }
        BlockState replacement = PortalCutsClient.replacement(pos, cir.getReturnValue());
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
