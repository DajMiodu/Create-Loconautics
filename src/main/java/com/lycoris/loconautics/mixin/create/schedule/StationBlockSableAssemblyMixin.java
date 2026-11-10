package com.lycoris.loconautics.mixin.create.schedule;

import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.station.StationBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = StationBlock.class, remap = false)
public abstract class StationBlockSableAssemblyMixin implements BlockSubLevelAssemblyListener {

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        TrackTargetingBehaviour<?> oldTarget = BlockEntityBehaviour.get(originLevel, oldPos, TrackTargetingBehaviour.TYPE);
        TrackTargetingBehaviour<?> newTarget = BlockEntityBehaviour.get(resultingLevel, newPos, TrackTargetingBehaviour.TYPE);
        if (oldTarget == null || newTarget == null) {
            return;
        }
        TrackEdgePoint point = oldTarget.getEdgePoint();
        if (point == null) {
            return;
        }
        CompoundTag data = new CompoundTag();
        DimensionPalette dimensions = new DimensionPalette();
        point.write(data, resultingLevel.registryAccess(), dimensions);
        dimensions.write(data);
        newTarget.invalidateEdgePoint(data);
    }
}
