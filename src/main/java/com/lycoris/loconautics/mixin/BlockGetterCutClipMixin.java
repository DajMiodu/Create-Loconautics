package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.trainphysics.portal.PortalCutLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// remapfalse like the other vanilla mixins here, the target is the mojang name
@Mixin(value = BlockGetter.class, remap = false)
public interface BlockGetterCutClipMixin {

    @Inject(method = "clipWithInteractionOverride", at = @At("HEAD"), cancellable = true)
    private void loconautics$skipCutBlocks(Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state,
                                           CallbackInfoReturnable<BlockHitResult> cir) {
        if (this instanceof Level level && PortalCutLookup.hidden(level, pos)) {
            cir.setReturnValue(null);
        }
    }
}
