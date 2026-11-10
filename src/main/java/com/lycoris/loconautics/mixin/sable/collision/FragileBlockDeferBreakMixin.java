package com.lycoris.loconautics.mixin.sable.collision;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback.CollisionResult;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.physics.callback.FragileBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FragileBlockCallback.class, remap = false)
public abstract class FragileBlockDeferBreakMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$deferFragileBreak(ServerLevel level, BlockPos pos, BlockState state, Vector3d hitPos,
            CallbackInfoReturnable<CollisionResult> cir) {
        level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
            if (!level.getBlockState(pos).equals(state)) {
                return;
            }
            level.destroyBlock(pos, true);
            if (state.getBlock() instanceof IceBlock) {
                BlockState below = level.getBlockState(pos.below());
                if (below.blocksMotion() || below.liquid()) {
                    level.setBlockAndUpdate(pos, IceBlock.meltsInto());
                }
            }
        }));
        cir.setReturnValue(new CollisionResult(JOMLConversion.ZERO, true));
    }
}
