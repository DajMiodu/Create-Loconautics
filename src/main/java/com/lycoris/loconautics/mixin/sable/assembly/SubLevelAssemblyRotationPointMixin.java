package com.lycoris.loconautics.mixin.sable.assembly;

import com.lycoris.loconautics.common.SubLevelAssemblyUtil;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// a split island always has a center of mass, so assembleblocks skips its rotation point and it pivots on the plot origin
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyRotationPointMixin {

    @Inject(method = "assembleBlocks", at = @At("RETURN"))
    private static void loconautics$anchorRotationPoint(ServerLevel level, BlockPos anchor,
                                                        Iterable<BlockPos> blocks, BoundingBox3ic bounds,
                                                        CallbackInfoReturnable<ServerSubLevel> cir) {
        ServerSubLevel sub = cir.getReturnValue();
        if (sub != null && !sub.isRemoved()) {
            SubLevelAssemblyUtil.anchorRotationPoint(sub);
        }
    }
}
