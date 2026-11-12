package com.lycoris.loconautics.compat.create.patch;

import com.lycoris.loconautics.compat.create.psi.SablePsiHandlers;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.actors.TrainActorControls;
import com.lycoris.loconautics.trainphysics.actors.TrainActorDrops;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RollerWorldActor {
    static final int HEADROOM_CLEAR_DEPTH = 2;


    private static final int TUNNEL_PAVE_MODE = 0;

    private static final Map<Long, Vec3> lastCenter = new HashMap<>();

    public static void onPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        ServerLevel serverLevel = system.getLevel();
        if (!LoconauticsConfig.enableRollerPatch.get()) return;

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;

        for (SubLevelAccess access : container.getAllSubLevels()) {
            if (!(access instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) continue;
            if (GhostWheelBodies.isWheelBody(subLevel.getUniqueId())) continue;
            SableTrain train = SableTrainRegistry.bySubLevel(subLevel.getUniqueId());
            if (train != null && train.isDerailed()) continue;
            for (RollerBlockEntity roller : SubLevelBlocks.findAll(subLevel, RollerBlockEntity.class)) {
                tick(roller, subLevel, serverLevel);
            }
        }
    }

    private static void tick(RollerBlockEntity roller, ServerSubLevel subLevel, ServerLevel world) {
        BlockState state = roller.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return;

        Direction localFacing = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos localPos = roller.getBlockPos();
        WorldActorUtil.ForwardMotion moved =
                WorldActorUtil.advanced(subLevel, localPos, localFacing, lastCenter);
        if (moved == null) return;
        Direction worldUp = moved.worldUp();

        Vec3 localPaving = Vec3.atCenterOf(localPos).add(localFacing.getStepX() * 0.5, -2, localFacing.getStepZ() * 0.5);
        BlockPos pavingPos = BlockPos.containing(subLevel.logicalPose().transformPosition(localPaving));

        if (TrainActorControls.isDisabled(roller)) return;

        BlockState paveWith = resolvePavingBlock(roller);
        if (paveWith == null || paveWith.isAir()) return;

        if (GhostWheelBodies.isWheelBodyAt(world, pavingPos)) return;

        if (world.getBlockState(pavingPos).is(paveWith.getBlock())) return;
        if (!consumePavingItem(subLevel, roller)) return;

        if (roller.mode.getValue() == TUNNEL_PAVE_MODE) {
            clearHeadroom(world, localPos, pavingPos, worldUp);
        }
        pave(world, pavingPos, paveWith);
    }

    private static void clearHeadroom(ServerLevel world, BlockPos rollerPos, BlockPos surface, Direction up) {
        int depth = HEADROOM_CLEAR_DEPTH;
        for (int i = 1; i <= depth; i++) {
            BlockPos above = surface.relative(up, i);
            if (GhostWheelBodies.isWheelBodyAt(world, above)) continue;
            BlockState state = world.getBlockState(above);
            if (canBreak(world, above, state)) {
                breakInto(world, rollerPos, above, state);
            }
        }
    }

    private static boolean canBreak(ServerLevel world, BlockPos pos, BlockState state) {
        if (!BlockBreakingKineticBlockEntity.isBreakable(state, state.getDestroySpeed(world, pos))) return false;
        if (AllTags.AllBlockTags.TRACKS.matches(state)) return false;
        for (Direction side : Direction.values()) {
            if (world.getBlockState(pos.relative(side)).is(BlockTags.PORTALS)) return false;
        }
        return true;
    }

    private static void breakInto(ServerLevel world, BlockPos rollerPos, BlockPos pos, BlockState state) {
        boolean noHarvest = state.is(BlockTags.NEEDS_IRON_TOOL) || state.is(BlockTags.NEEDS_STONE_TOOL)
                || state.is(BlockTags.NEEDS_DIAMOND_TOOL);
        Vec3 center = Vec3.atCenterOf(pos);
        BlockHelper.destroyBlock(world, pos, 1f, stack -> {
            if (stack.isEmpty() || noHarvest || world.random.nextBoolean()) return;
            if (TrainActorDrops.depositInPlace(world, rollerPos, stack)) return;
            ItemEntity dropped = new ItemEntity(world, center.x, center.y, center.z, stack);
            dropped.setDefaultPickUpDelay();
            world.addFreshEntity(dropped);
        });
    }

    private static void pave(ServerLevel world, BlockPos pos, BlockState paveWith) {
        BlockState existing = world.getBlockState(pos);
        if (existing.is(paveWith.getBlock())) return;
        if (existing.getDestroySpeed(world, pos) < 0) return;
        world.setBlockAndUpdate(pos, paveWith);
    }

    private static boolean consumePavingItem(ServerSubLevel subLevel, RollerBlockEntity roller) {
        FilteringBehaviour filter = roller.getBehaviour(FilteringBehaviour.TYPE);
        if (filter == null) return false;
        ItemStack wanted = filter.getFilter();
        if (wanted.isEmpty()) return false;

        IItemHandlerModifiable items = SablePsiHandlers.carriageItems(subLevel, () -> {});
        ItemStack taken = ItemHelper.extract(items, stack -> ItemStack.isSameItemSameComponents(stack, wanted), 1, false);
        return !taken.isEmpty();
    }

    private static BlockState resolvePavingBlock(RollerBlockEntity roller) {
        FilteringBehaviour filter = roller.getBehaviour(FilteringBehaviour.TYPE);
        if (filter == null) return null;
        ItemStack held = filter.getFilter();
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem bi)) return null;
        return bi.getBlock().defaultBlockState();
    }



    public static void invalidate(UUID subLevelId) { WorldActorUtil.invalidate(lastCenter, subLevelId); }
}