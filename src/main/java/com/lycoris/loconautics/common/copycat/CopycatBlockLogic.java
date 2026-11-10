package com.lycoris.loconautics.common.copycat;

import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public final class CopycatBlockLogic {

    private CopycatBlockLogic() {}

    public static boolean hasSupportBehind(LevelReader level, BlockPos pos, Direction facing) {
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        return !support.getBlockSupportShape(level, supportPos).getFaceShape(facing).isEmpty();
    }

    public static ModelData modelData(CopycatMaterialStore store) {
        return ModelData.builder().with(CopycatModel.MATERIAL_PROPERTY, store.material()).build();
    }

    @Nullable
    public static CopycatMaterialStore storeAt(LevelAccessor level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CopycatMaterialHolder holder ? holder.copycat() : null;
    }

    @Nullable
    public static BlockState acceptedMaterial(Level level, BlockPos pos, ItemStack stack, @Nullable Direction face) {
        if (!(stack.getItem() instanceof BlockItem item) || item.getBlock() instanceof CopycatBlock) {
            return null;
        }
        Block block = item.getBlock();
        BlockState applied = block.defaultBlockState();
        if (!AllBlockTags.COPYCAT_ALLOW.matches(block)) {
            if (AllBlockTags.COPYCAT_DENY.matches(block) || block instanceof EntityBlock || block instanceof StairBlock) {
                return null;
            }
            VoxelShape shape = applied.getShape(level, pos);
            if (shape.isEmpty() || !shape.bounds().equals(Shapes.block().bounds())
                    || applied.getCollisionShape(level, pos).isEmpty()) {
                return null;
            }
        }
        if (face == null) {
            return applied;
        }
        Direction.Axis axis = face.getAxis();
        if (applied.hasProperty(BlockStateProperties.FACING)) {
            applied = applied.setValue(BlockStateProperties.FACING, face);
        }
        if (applied.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && axis != Direction.Axis.Y) {
            applied = applied.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
        }
        if (applied.hasProperty(BlockStateProperties.AXIS)) {
            applied = applied.setValue(BlockStateProperties.AXIS, axis);
        }
        if (applied.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && axis != Direction.Axis.Y) {
            applied = applied.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
        }
        return applied;
    }

    public static ItemInteractionResult useItemOn(ItemStack stack, Level level, BlockPos pos, @Nullable Player player,
                                                  Direction face) {
        CopycatMaterialStore store = storeAt(level, pos);
        BlockState offered = store == null || player == null ? null : acceptedMaterial(level, pos, stack, face);
        if (offered == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (store.material().is(offered.getBlock())) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            if (!store.cycleMaterial()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f);
            return ItemInteractionResult.SUCCESS;
        }
        if (store.hasCustomMaterial()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        store.setMaterial(offered);
        store.setConsumed(stack);
        level.playSound(null, pos, offered.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.75f);
        if (player.isCreative()) {
            return ItemInteractionResult.SUCCESS;
        }
        stack.shrink(1);
        return ItemInteractionResult.SUCCESS;
    }

    public static InteractionResult cyclePlacement(BlockState state, Level level, BlockPos pos) {
        if (!state.hasProperty(CopycatPlacement.PLACEMENT)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, state.setValue(CopycatPlacement.PLACEMENT,
                state.getValue(CopycatPlacement.PLACEMENT).next()), 3);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, 1.1f);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult reclaim(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        CopycatMaterialStore store = storeAt(level, pos);
        if (store == null || !store.hasCustomMaterial()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player != null && !player.isCreative()) {
            player.getInventory().placeItemBackInInventory(store.consumed());
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(store.material()));
        store.reset();
        return InteractionResult.SUCCESS;
    }

    public static void placedBy(Level level, BlockPos pos, @Nullable LivingEntity placer) {
        CopycatMaterialStore store = storeAt(level, pos);
        if (store == null || placer == null || store.hasCustomMaterial()) {
            return;
        }
        ItemStack offhand = placer.getItemInHand(InteractionHand.OFF_HAND);
        BlockState material = acceptedMaterial(level, pos, offhand, Direction.orderedByNearest(placer)[0]);
        if (material == null) {
            return;
        }
        store.setMaterial(material);
        store.setConsumed(offhand);
        if (placer instanceof Player player && player.isCreative()) {
            return;
        }
        offhand.shrink(1);
    }

    public static void popConsumed(Level level, BlockPos pos) {
        CopycatMaterialStore store = storeAt(level, pos);
        if (store == null || level.isClientSide || store.consumed().isEmpty()) {
            return;
        }
        Block.popResource(level, pos, store.consumed());
    }
}
