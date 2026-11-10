package com.lycoris.loconautics.common.sable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation.DiscoveredLocation;
import com.simibubi.create.content.trains.signal.SingleBlockEntityEdgePoint;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class StationTrackRebind {

    private static final String TARGET_TRACK_KEY = "TargetTrack";
    private static final String BEZIER_KEY = "Bezier";
    private static final String BEZIER_CURVE_KEY = "Key";

    private record Pending(ServerLevel level, BlockPos station) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<BlockPos, BlockPos> MOVED = new HashMap<>();

    private StationTrackRebind() {
    }

    public static void capture(ServerLevel level, BlockPos oldTrack, BlockPos newTrack) {
        MOVED.put(oldTrack.immutable(), newTrack.immutable());
        BlockState state = level.getBlockState(oldTrack);
        if (!(state.getBlock() instanceof TrackBlock track)) {
            return;
        }
        Set<BlockPos> seen = new HashSet<>();
        for (DiscoveredLocation end : track.getConnected(level, oldTrack, state, false, null)) {
            for (TrackGraph graph : Create.RAILWAYS.getGraphs(level, end)) {
                TrackNode node = graph.locateNode(end);
                for (TrackEdge edge : graph.getConnectionsFrom(node).values()) {
                    for (TrackEdgePoint point : edge.getEdgeData().getPoints()) {
                        if (point instanceof SingleBlockEntityEdgePoint single
                                && level.dimension().equals(single.getBlockEntityDimension())
                                && seen.add(single.getBlockEntityPos())) {
                            PENDING.add(new Pending(level, single.getBlockEntityPos().immutable()));
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            MOVED.clear();
            return;
        }
        List<Pending> work = List.copyOf(PENDING);
        PENDING.clear();
        for (Pending pending : work) {
            rebind(pending);
        }
        MOVED.clear();
    }

    private static void rebind(Pending pending) {
        TrackTargetingBehaviour<?> target = BlockEntityBehaviour.get(pending.level(), pending.station(), TrackTargetingBehaviour.TYPE);
        if (target == null) {
            return;
        }
        BlockPos oldTrack = target.getGlobalPosition();
        BlockPos newTrack = MOVED.get(oldTrack);
        if (newTrack == null) {
            return;
        }
        HolderLookup.Provider registries = pending.level().registryAccess();
        CompoundTag tag = new CompoundTag();
        target.write(tag, registries, false);
        tag.put(TARGET_TRACK_KEY, NbtUtils.writeBlockPos(newTrack.subtract(pending.station())));
        if (tag.contains(BEZIER_KEY)) {
            CompoundTag bezier = tag.getCompound(BEZIER_KEY);
            BlockPos curve = NBTHelper.readBlockPos(bezier, BEZIER_CURVE_KEY);
            bezier.put(BEZIER_CURVE_KEY, NbtUtils.writeBlockPos(curve.offset(newTrack.subtract(oldTrack))));
        }
        target.read(tag, registries, false);
        target.blockEntity.notifyUpdate();
    }
}
