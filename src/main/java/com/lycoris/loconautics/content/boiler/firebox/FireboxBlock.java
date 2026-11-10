package com.lycoris.loconautics.content.boiler.firebox;


import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.client.connected.ConnectedBlock;
import com.lycoris.loconautics.client.connected.ConnectionBounds;
import com.lycoris.loconautics.client.copycat.CopycatMaterialSource;
import com.lycoris.loconautics.client.copycat.CopycatQuads;
import com.lycoris.loconautics.compat.cbc.BoilerImpactCallback;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FireboxBlock extends BaseEntityBlock
        implements BlockWithSubLevelCollisionCallback, BlockSubLevelAssemblyListener, IWrenchable, ConnectedBlock,
        CopycatMaterialSource {

    public static final BooleanProperty EXTENSION = BooleanProperty.create("extension");


    public FireboxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.LIT, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(EXTENSION, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(FireboxBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT, BlockStateProperties.HORIZONTAL_FACING, EXTENSION);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, ShiftPlacement.facing(context))
                .setValue(EXTENSION, absorbingController(context.getLevel(), context.getClickedPos()) != null);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Nullable
    private static BlockPos absorbingController(BlockGetter level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos controller = FireboxGroups.controllerOf(level, pos.relative(dir));
            if (controller == null || controller.equals(pos)) continue;
            if (!FireboxGroups.canAbsorb(level, controller, java.util.List.of(pos))) continue;
            return controller;
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;

        BlockPos controller = absorbingController(level, pos);
        if (controller != null) {
            FireboxGroups.adopt(level, controller, java.util.List.of(pos));
            absorbNeighbors(level, controller);
            FireboxGroups.freeDoor(level, controller);
            return;
        }

        if (state.getValue(EXTENSION)) {
            level.setBlock(pos, state.setValue(EXTENSION, false), 3);
        }
        absorbNeighbors(level, pos);
        FireboxGroups.freeDoor(level, pos);
        FireboxGroups.rerender(level, pos);
    }

    // two half built fireboxes touching would otherwise sit there as separate controllers
    private static void absorbNeighbors(Level level, BlockPos controller) {
        for (BlockPos member : FireboxGroups.members(level, controller)) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos other = FireboxGroups.controllerOf(level, member.relative(dir));
                if (other == null || other.equals(controller)) continue;

                java.util.Set<BlockPos> theirs = FireboxGroups.members(level, other);
                if (!FireboxGroups.canAbsorb(level, controller, theirs)) continue;

                if (level.getBlockEntity(other) instanceof FireboxBlockEntity oldController
                        && level.getBlockEntity(controller) instanceof FireboxBlockEntity kept
                        && kept.firstFuel().isEmpty()) {
                    FireboxBlockEntity.transferControllerData(oldController, level, other, controller, dir);
                }
                FireboxGroups.adopt(level, controller, theirs);
                return;
            }
        }
    }

    @Override
    public boolean connectsTo(BlockGetter level, BlockState state, BlockPos anchor, BlockPos neighborPos) {
        return anchor.equals(FireboxGroups.renderAnchor(level, neighborPos));
    }

    @Override
    public ConnectionBounds connectionBounds() {
        return FireboxGroups.BOUNDS;
    }

    @Override
    public BlockPos groupAnchor(BlockGetter level, BlockState state, BlockPos pos) {
        BlockPos anchor = FireboxGroups.renderAnchor(level, pos);
        return anchor != null ? anchor : pos;
    }

    @Override
    public @Nullable BlockState copycatMaterial(net.minecraft.world.level.BlockAndTintGetter level,
                                                BlockPos pos, BlockState state) {
        BlockPos controller = FireboxGroups.renderAnchor(level, pos);
        if (controller == null) return null;
        if (!(level.getBlockEntity(controller) instanceof FireboxBlockEntity be)) return null;

        ItemStack fuel = be.firstFuel();
        return fuel.isEmpty() ? null : CopycatQuads.materialOf(fuel);
    }

    @Override
    public boolean isOpenFace(BlockState state, Direction face) {
        return !state.getValue(EXTENSION)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == face;
    }

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return BoilerImpactCallback.INSTANCE;
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        if (resultingLevel.getBlockEntity(newPos) instanceof FireboxBlockEntity be) {
            be.shiftReferences(newPos.subtract(oldPos));
        }
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 20;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (state.getValue(EXTENSION)) return InteractionResult.PASS;
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) return InteractionResult.PASS;
        if (FireboxBlockEntity.isFuel(player.getOffhandItem())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof FireboxBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        boolean open = controller.toggleDoors();
        level.playSound(null, pos,
                open ? net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN
                        : net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.1f);
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (state.getValue(EXTENSION)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof FireboxBlockEntity controller)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof net.minecraft.world.item.ShovelItem) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (controller.isLockedOut()) {
                controller.clearLockout();
            } else {
                controller.dropFuel();
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (!FireboxBlockEntity.isFuel(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return controller.insertFuel(player, stack)
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();

        if (face.getAxis() == Direction.Axis.Y) return InteractionResult.PASS;

        if (level.isClientSide) return InteractionResult.SUCCESS;

        boolean isExtension = state.getValue(EXTENSION);

        if (!isExtension) {
            level.setBlock(pos, state.setValue(BlockStateProperties.HORIZONTAL_FACING, face), 3);
            FireboxGroups.freeDoor(level, pos);
            IWrenchable.playRotateSound(level, pos);
            return InteractionResult.SUCCESS;
        }

        BlockPos controllerPos = FireboxGroups.controllerOf(level, pos);
        java.util.Set<BlockPos> group = controllerPos == null
                ? java.util.Set.of(pos.immutable())
                : FireboxGroups.members(level, controllerPos);

        if (controllerPos != null && level.getBlockEntity(controllerPos) instanceof FireboxBlockEntity old) {
            FireboxBlockEntity.transferControllerData(old, level, controllerPos, pos, face);
        }

        level.setBlock(pos, state.setValue(EXTENSION, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, face), 3);
        FireboxGroups.adopt(level, pos, group);
        FireboxGroups.freeDoor(level, pos);

        IWrenchable.playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !movedByPiston) {
            if (state.getValue(EXTENSION)) {
                BlockPos controller = level.getBlockEntity(pos) instanceof FireboxBlockEntity be
                        ? be.getControllerPos() : null;
                if (controller != null && !controller.equals(pos)) {
                    FireboxGroups.reseat(level, controller, pos);
                }
            } else {
                handOverOrDrop(level, pos, state);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void handOverOrDrop(Level level, BlockPos removedPos, BlockState removedState) {
        java.util.Set<BlockPos> remaining = new java.util.LinkedHashSet<>(
                FireboxGroups.members(level, removedPos));
        remaining.remove(removedPos);

        Direction preferred = removedState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos target = null;
        for (Direction dir : orderedFromFacing(preferred)) {
            BlockPos neighbor = removedPos.relative(dir);
            if (remaining.contains(neighbor)) {
                target = neighbor;
                preferred = findExposedFace(level, neighbor, dir);
                break;
            }
        }
        if (target == null && !remaining.isEmpty()) target = remaining.iterator().next();

        if (target == null) {
            if (level.getBlockEntity(removedPos) instanceof FireboxBlockEntity controller) {
                controller.dropFuel();
                controller.parkWaterInBarrels();
            }
            return;
        }

        if (level.getBlockEntity(removedPos) instanceof FireboxBlockEntity old) {
            FireboxBlockEntity.transferControllerData(old, level, removedPos, target, preferred);
        }

        java.util.Set<BlockPos> island = FireboxGroups.component(level, target, removedPos);
        level.setBlock(target, level.getBlockState(target)
                .setValue(EXTENSION, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, preferred), 3);
        FireboxGroups.adopt(level, target, island);

        if (level.getBlockEntity(target) instanceof FireboxBlockEntity be) {
            be.onBecameController();
        }

        for (BlockPos leftover : remaining) {
            FireboxGroups.promoteIfOrphaned(level, removedPos, leftover, island);
        }
    }

    private Direction findExposedFace(Level level, BlockPos pos, Direction blockedFrom) {
        Direction best = blockedFrom.getOpposite();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir == blockedFrom) continue;
            BlockPos neighbor = pos.relative(dir);
            if (level.getBlockState(neighbor).isAir() || !level.getBlockState(neighbor).isSolid()) {
                return dir;
            }
        }
        return best;
    }

    private Direction[] orderedFromFacing(Direction preferred) {
        Direction[] horiz = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
        Direction[] result = new Direction[4];
        int i = 0;
        result[i++] = preferred;
        for (Direction d : horiz) {
            if (d != preferred) result[i++] = d;
        }
        return result;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BoilerBlocks.createFireboxBE(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BoilerBlocks.FIREBOX_BE.get()) return null;
        if (state.getValue(EXTENSION)) return null;
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> ((FireboxBlockEntity) be).clientTick();
        }
        return (lvl, pos, st, be) -> ((FireboxBlockEntity) be).tick();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
