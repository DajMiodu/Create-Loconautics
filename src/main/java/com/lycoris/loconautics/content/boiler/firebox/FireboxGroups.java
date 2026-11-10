package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.client.connected.ConnectionBounds;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FireboxGroups {

    public static final ConnectionBounds BOUNDS = ConnectionBounds.FLAT_3X3;

    private FireboxGroups() {}

    public static boolean isFirebox(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(BoilerTags.BOILER_FIREBOX);
    }

    @Nullable
    public static BlockPos controllerOf(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BoilerTags.BOILER_FIREBOX)) return null;
        if (!state.getValue(FireboxBlock.EXTENSION)) return pos.immutable();

        if (!(level.getBlockEntity(pos) instanceof FireboxBlockEntity be)) return null;
        BlockPos owner = be.getControllerPos();
        if (owner == null) return null;

        BlockState ownerState = level.getBlockState(owner);
        if (!ownerState.is(BoilerTags.BOILER_FIREBOX)) return null;
        return ownerState.getValue(FireboxBlock.EXTENSION) ? null : owner;
    }

    @Nullable
    public static BlockPos renderAnchor(BlockGetter level, BlockPos pos) {
        BlockPos controller = controllerOf(level, pos);
        if (controller != null) return controller;

        BlockPos owner = storedOwner(level, pos);
        if (owner == null) return null;

        BlockPos anchor = null;
        for (BlockPos member : component(level, pos, owner)) {
            if (anchor == null || member.asLong() < anchor.asLong()) anchor = member;
        }
        return anchor;
    }

    @Nullable
    public static BlockPos storedOwner(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BoilerTags.BOILER_FIREBOX)) return null;
        if (!state.getValue(FireboxBlock.EXTENSION)) return pos.immutable();
        return level.getBlockEntity(pos) instanceof FireboxBlockEntity be ? be.getControllerPos() : null;
    }

    public static Set<BlockPos> members(BlockGetter level, BlockPos controller) {
        return component(level, controller, controller);
    }

    public static Set<BlockPos> component(BlockGetter level, BlockPos start, BlockPos owner) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        found.add(start.immutable());
        pending.add(start.immutable());

        while (!pending.isEmpty()) {
            BlockPos current = pending.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);
                if (found.contains(next)) continue;
                if (!owner.equals(storedOwner(level, next))) continue;
                found.add(next.immutable());
                pending.add(next.immutable());
            }
        }
        return found;
    }

    public static void reseat(Level level, BlockPos controller, BlockPos removed) {
        Set<BlockPos> kept = members(level, controller);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            promoteIfOrphaned(level, controller, removed.relative(dir), kept);
        }
        rerender(level, controller);
    }

    public static void promoteIfOrphaned(Level level, BlockPos owner, BlockPos pos, Set<BlockPos> kept) {
        if (kept.contains(pos)) return;
        if (!owner.equals(storedOwner(level, pos))) return;

        Set<BlockPos> island = component(level, pos, owner);
        level.setBlock(pos, level.getBlockState(pos).setValue(FireboxBlock.EXTENSION, false), 3);
        adopt(level, pos, island);
        if (level.getBlockEntity(pos) instanceof FireboxBlockEntity be) be.onBecameController();
    }

    public static void freeDoor(Level level, BlockPos controller) {
        Set<BlockPos> group = members(level, controller);
        BlockPos current = controller;

        for (int step = 0; step < group.size(); step++) {
            BlockState state = level.getBlockState(current);
            if (!state.is(BoilerTags.BOILER_FIREBOX)) return;

            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos ahead = current.relative(facing);
            if (!group.contains(ahead)) return;

            if (level.getBlockEntity(current) instanceof FireboxBlockEntity old) {
                FireboxBlockEntity.transferControllerData(old, level, current, ahead, facing);
            }
            level.setBlock(ahead, level.getBlockState(ahead)
                    .setValue(FireboxBlock.EXTENSION, false)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 3);
            adopt(level, ahead, group);
            if (level.getBlockEntity(ahead) instanceof FireboxBlockEntity be) be.onBecameController();

            current = ahead;
        }
    }

    public static boolean canAbsorb(BlockGetter level, BlockPos controller, Collection<BlockPos> extra) {
        Set<BlockPos> combined = new HashSet<>(members(level, controller));
        for (BlockPos pos : extra) {
            if (!BOUNDS.fits(combined, pos)) return false;
            combined.add(pos.immutable());
        }
        return true;
    }

    public static void adopt(Level level, BlockPos controller, Collection<BlockPos> members) {
        for (BlockPos pos : members) {
            if (pos.equals(controller)) continue;

            BlockState state = level.getBlockState(pos);
            if (!state.is(BoilerTags.BOILER_FIREBOX)) continue;
            if (!state.getValue(FireboxBlock.EXTENSION)) {
                level.setBlock(pos, state.setValue(FireboxBlock.EXTENSION, true)
                        .setValue(BlockStateProperties.LIT, false), 3);
            }
            if (level.getBlockEntity(pos) instanceof FireboxBlockEntity be) {
                be.setControllerPos(controller);
            }
        }
        if (level.getBlockEntity(controller) instanceof FireboxBlockEntity be) {
            be.setControllerPos(null);
        }
        rerender(level, controller);
    }

    public static void rerender(Level level, BlockPos controller) {
        for (BlockPos pos : members(level, controller)) {
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }
}
