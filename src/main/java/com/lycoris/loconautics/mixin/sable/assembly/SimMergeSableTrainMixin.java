package com.lycoris.loconautics.mixin.sable.assembly;

import com.lycoris.loconautics.trainphysics.spawn.ArticulationLinks;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimAssemblyHelper.class, remap = false)
public abstract class SimMergeSableTrainMixin {

    @Inject(method = "disassembleSubLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void loconautics$keepHingedCarsApart(Level level, SubLevel toDisassemble, BlockPos subLevelAnchor,
                                                        BlockPos disassemblyGoal, Rotation rotation, boolean playSound,
                                                        CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(Sable.HELPER.getContaining(serverLevel, disassemblyGoal) instanceof ServerSubLevel host)) {
            return;
        }
        if (ArticulationLinks.hinged(serverLevel, toDisassemble.getUniqueId(), host.getUniqueId())) {
            ci.cancel();
        }
    }

    @Inject(method = "disassembleSubLevel", at = @At("TAIL"), remap = false)
    private static void loconautics$mergeTrains(Level level, SubLevel toDisassemble, BlockPos subLevelAnchor,
                                                BlockPos disassemblyGoal, Rotation rotation, boolean playSound,
                                                CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || !(toDisassemble instanceof ServerSubLevel gone)) {
            return;
        }
        if (!(Sable.HELPER.getContaining(serverLevel, disassemblyGoal) instanceof ServerSubLevel host)) {
            return;
        }
        SubLevelMergeWatcher.onMerged(serverLevel, gone.getUniqueId(), host.getUniqueId());
    }
}
