package com.lycoris.loconautics.trainphysics.actors;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainActorControls {

    private record Snapshot(long gameTime, List<ItemStack> filters) {}

    private static final Map<UUID, Snapshot> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Snapshot> CLIENT = new ConcurrentHashMap<>();

    private TrainActorControls() {
    }

    public static boolean isDisabled(BlockEntity be) {
        if (be == null || be.getLevel() == null) {
            return false;
        }
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (!controllable(state)) {
            return false;
        }
        SubLevel sub = level.isClientSide
                ? Sable.HELPER.getContainingClient(be)
                : Sable.HELPER.getContaining(be);
        return disabledIn(level, sub, state);
    }

    public static boolean isDisabled(Level level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || !controllable(state)) {
            return false;
        }
        SubLevel sub = level.isClientSide
                ? Sable.HELPER.getContainingClient(pos)
                : Sable.HELPER.getContaining(level, pos);
        return disabledIn(level, sub, state);
    }

    public static boolean onTrain(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        SubLevel sub = level.isClientSide
                ? Sable.HELPER.getContainingClient(pos)
                : Sable.HELPER.getContaining(level, pos);
        if (sub == null || sub.isRemoved() || sub.getUniqueId() == null) {
            return false;
        }
        return isTrain(level, sub.getUniqueId());
    }

    public static void clearServer() {
        SERVER.clear();
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    private static boolean controllable(BlockState state) {
        return AllItemTags.CONTRAPTION_CONTROLLED.matches(state.getBlock().asItem());
    }

    private static boolean disabledIn(Level level, SubLevel sub, BlockState state) {
        if (sub == null || sub.isRemoved()) {
            return false;
        }
        UUID id = sub.getUniqueId();
        if (id == null || !isTrain(level, id)) {
            return false;
        }
        List<ItemStack> filters = filters(level, sub, id);
        if (filters.isEmpty()) {
            return false;
        }
        ItemStack machine = new ItemStack(state.getBlock());
        for (ItemStack filter : filters) {
            if (filter.isEmpty() || ContraptionControlsMovement.isSameFilter(filter, machine)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrain(Level level, UUID subLevelId) {
        return level.isClientSide
                ? SableTrainClientRegistry.isTrain(subLevelId)
                : SableTrainRegistry.bySubLevel(subLevelId) != null;
    }

    private static List<ItemStack> filters(Level level, SubLevel sub, UUID id) {
        Map<UUID, Snapshot> cache = level.isClientSide ? CLIENT : SERVER;
        long now = level.getGameTime();
        Snapshot cached = cache.get(id);
        if (cached != null && cached.gameTime() == now) {
            return cached.filters();
        }
        List<ItemStack> found = new ArrayList<>();
        for (ContraptionControlsBlockEntity controls
                : SubLevelBlocks.findAll(sub, ContraptionControlsBlockEntity.class)) {
            if (controls.disabled) {
                found.add(controls.filtering.getFilter());
            }
        }
        cache.put(id, new Snapshot(now, found));
        return found;
    }
}
