package com.lycoris.loconautics.mixin.create.track;

import com.lycoris.loconautics.common.sable.StationTrackRebind;
import com.simibubi.create.content.trains.track.TrackBlock;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TrackBlock.class, remap = false)
public abstract class TrackBlockSableAssemblyMixin implements BlockSubLevelAssemblyListener {

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        StationTrackRebind.capture(originLevel, oldPos, newPos);
        resultingLevel.scheduleTick(newPos, (Block) (Object) this, 1);
    }
}
