package com.lycoris.loconautics.content.casingpicker;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.BogeyGroundDrop;
import com.lycoris.loconautics.compat.railways.RailwaysBogeyChoice;
import com.lycoris.loconautics.foundation.ui.GuiCroppedItem;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class BogeyItem extends Item implements GuiCroppedItem {

    private record Placement(BlockPos target, BlockState state, boolean onTrack, boolean grounded) {
    }

    public BogeyItem(Properties properties) {
        super(properties);
    }

    public static BogeyChoice choiceOf(ItemStack stack) {
        return stack.getOrDefault(LoconauticsRegistries.BOGEY_CHOICE.get(), BogeyChoice.CREATE_STANDARD);
    }

    public static ItemStack of(BogeyChoice choice, int count) {
        ItemStack stack = new ItemStack(LoconauticsRegistries.BOGEY.get(), count);
        stack.set(LoconauticsRegistries.BOGEY_CHOICE.get(), choice);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return choiceOf(stack).style().displayName;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);
        BogeyChoice choice = choiceOf(stack);
        if (clickedState.getBlock() instanceof ITrackBlock fitted && ModList.get().isLoaded("railways")) {
            choice = fittedToTrack(choice, fitted);
        }
        Placement placement = clickedState.getBlock() instanceof ITrackBlock track
                ? onTrack(level, player, track, clickedState, clicked, choice)
                : onGround(context, choice);
        if (placement == null || !player.mayUseItemAt(placement.target(), context.getClickedFace(), stack)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        place(level, player, stack, placement, choice);
        return InteractionResult.CONSUME;
    }

    private static BogeyChoice fittedToTrack(BogeyChoice choice, ITrackBlock track) {
        if (LoconauticsConstants.RAILWAYS_MONORAIL_MATERIAL.equals(track.getMaterial().id)) {
            return BogeyChoice.CREATE_STANDARD;
        }
        return RailwaysBogeyChoice.fitTo(choice, track.getMaterial().trackType);
    }

    private static Placement onTrack(Level level, Player player, ITrackBlock track, BlockState trackState,
                                     BlockPos pos, BogeyChoice choice) {
        BlockState anchor = track.getBogeyAnchor(level, pos, trackState);
        if (!choice.isStandard() && anchor.hasProperty(AbstractBogeyBlock.AXIS)) {
            anchor = choice.block().defaultBlockState()
                    .setValue(AbstractBogeyBlock.AXIS, anchor.getValue(AbstractBogeyBlock.AXIS));
        }
        boolean upsideDown = player.getViewXRot(1.0F) < 0
                && anchor.getBlock() instanceof AbstractBogeyBlock<?> bogey && bogey.canBeUpsideDown();
        Vec3 up = track.getUpNormal(level, pos, trackState);
        BlockPos target = pos.offset(BlockPos.containing(upsideDown ? up.scale(-1) : up));
        if (level.getBlockState(target).getDestroySpeed(level, target) == -1) return null;
        if (anchor.getBlock() instanceof AbstractBogeyBlock<?> bogey) {
            anchor = bogey.getVersion(anchor, upsideDown);
        }
        return new Placement(target, anchor, true, false);
    }

    private static Placement onGround(UseOnContext context, BogeyChoice choice) {
        BlockPos target = new BlockPlaceContext(context).getClickedPos();
        boolean floor = context.getClickedFace() == Direction.UP;
        if (floor) {
            target = target.above();
        }
        if (!context.getLevel().getBlockState(target).canBeReplaced()) return null;
        BlockState state = choice.block().defaultBlockState()
                .setValue(AbstractBogeyBlock.AXIS, context.getHorizontalDirection().getAxis());
        return new Placement(target, state, false, floor);
    }

    private static void place(Level level, Player player, ItemStack stack, Placement placement, BogeyChoice choice) {
        BlockPos target = placement.target();
        if (placement.onTrack()) {
            level.destroyBlock(target, true);
        }
        BlockState state = ProperWaterloggedBlock.withWater(level, placement.state(), target);
        level.setBlock(target, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(target) instanceof AbstractBogeyBlockEntity bogeyEntity) {
            if (!choice.isStandard()) {
                bogeyEntity.setBogeyStyle(choice.style());
            }
            BogeyGroundDrop.set(bogeyEntity, placement.grounded());
        }
        SoundType sound = state.getSoundType(level, target, player);
        level.playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static IClientItemExtensions clientExtensions() {
        return new IClientItemExtensions() {
            private final CustomRenderedItemModelRenderer renderer = new BogeyItemRenderer();

            @Override
            public CustomRenderedItemModelRenderer getCustomRenderer() {
                return renderer;
            }
        };
    }
}
