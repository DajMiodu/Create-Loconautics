package com.lycoris.loconautics.mixin.steelcable;

import com.lycoris.loconautics.common.strand.StrandRemap;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = RopeWinchBlock.class, remap = false)
public abstract class RopeWinchAssemblyMixin implements BlockSubLevelAssemblyListener {

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }
}
