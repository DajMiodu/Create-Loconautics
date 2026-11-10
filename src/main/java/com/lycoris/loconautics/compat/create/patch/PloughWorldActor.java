package com.lycoris.loconautics.compat.create.patch;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.actors.TrainActorControls;
import com.lycoris.loconautics.trainphysics.actors.TrainActorDrops;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.content.contraptions.actors.plough.PloughBlock;
import com.simibubi.create.content.trains.track.FakeTrackBlock;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PloughWorldActor {

    private static final Map<Long, Vec3> lastCenter = new HashMap<>();

    public static void onPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        ServerLevel serverLevel = system.getLevel();
        if (!LoconauticsConfig.enablePloughPatch.get()) return;

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;

        for (SubLevelAccess access : container.getAllSubLevels()) {
            if (!(access instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) continue;
            if (GhostWheelBodies.isWheelBody(subLevel.getUniqueId())) continue;
            SubLevelBlocks.forEachBlock(subLevel, (pos, state) -> {
                if (state.getBlock() instanceof PloughBlock) {
                    tick(pos.immutable(), state, subLevel, serverLevel);
                }
                return true;
            });
        }
    }

    private static void tick(BlockPos localPos, BlockState state, ServerSubLevel subLevel, ServerLevel world) {
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return;

        Direction localFacing = state.getValue(HorizontalDirectionalBlock.FACING);
        WorldActorUtil.ForwardMotion moved =
                WorldActorUtil.advanced(subLevel, localPos, localFacing, lastCenter);
        if (moved == null) return;
        Vec3 worldCenter = moved.worldCenter();
        Direction worldUp = moved.worldUp();

        if (isLocked(localPos, subLevel)) return;
        if (TrainActorControls.isDisabled(subLevel.getLevel(), localPos, state)) return;

        BlockPos aheadPos = BlockPos.containing(worldCenter);
        if (!GhostWheelBodies.isWheelBodyAt(world, aheadPos)) {
            breakAhead(world, subLevel, localPos, aheadPos);
        }

        BlockPos belowPos = aheadPos.relative(worldUp.getOpposite());
        if (GhostWheelBodies.isWheelBodyAt(world, belowPos)) return;
        till(world, belowPos, worldCenter);
    }

    private static void breakAhead(ServerLevel world, ServerSubLevel subLevel, BlockPos localPos, BlockPos target) {
        BlockState state = world.getBlockState(target);
        if (!canBreak(world, target, state)) return;

        BlockHelper.destroyBlock(world, target, 1f, stack -> {
            if (stack.isEmpty()) return;
            if (TrainActorDrops.depositInPlace(subLevel.getLevel(), localPos, stack)) return;
            Vec3 center = Vec3.atCenterOf(target);
            ItemEntity dropped = new ItemEntity(world, center.x, center.y, center.z, stack);
            dropped.setDefaultPickUpDelay();
            world.addFreshEntity(dropped);
        });
    }

    private static boolean canBreak(Level world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (world.getBlockState(pos.below()).getBlock() instanceof FarmBlock) return false;
        if (state.getBlock() instanceof LiquidBlock) return false;
        if (state.getBlock() instanceof BubbleColumnBlock) return false;
        if (state.getBlock() instanceof ITrackBlock) return true;
        if (state.getBlock() instanceof FakeTrackBlock) return false;
        if (AllBlockTags.PLOUGH_BLACKLIST.matches(state.getBlock())) return false;
        if (AllBlockTags.PLOUGH_WHITELIST.matches(state.getBlock())) return true;
        return state.getCollisionShape(world, pos).isEmpty();
    }

    private static void till(ServerLevel world, BlockPos target, Vec3 hitVec) {
        BlockState ground = world.getBlockState(target);
        if (ground.isAir() || ground.getDestroySpeed(world, target) < 0) return;

        FakePloughPlayer fakePlayer = FakePloughPlayer.get(world);
        ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, hoe);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, target, false);
        UseOnContext ctx = new UseOnContext(world, fakePlayer, InteractionHand.MAIN_HAND, hoe, hit);
        hoe.useOn(ctx);
    }

    private static boolean isLocked(BlockPos localPos, ServerSubLevel subLevel) {
        BlockEntity be = subLevel.getLevel().getBlockEntity(localPos);
        if (be instanceof PloughBlockEntity ploughBE) {
            return ploughBE.isLocked();
        }
        return false;
    }



    public static void invalidate(UUID subLevelId) { WorldActorUtil.invalidate(lastCenter, subLevelId); }
}