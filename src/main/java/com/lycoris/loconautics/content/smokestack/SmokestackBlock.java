package com.lycoris.loconautics.content.smokestack;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class SmokestackBlock extends Block implements IWrenchable, EntityBlock {

    public static final int MAX_HEIGHT = 6;

    public static final BooleanProperty STACKED = BooleanProperty.create("stacked");

    private static final VoxelShape CAPPED_SHAPE = Shapes.or(
            Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0),
            Block.box(1.0, 9.0, 1.0, 15.0, 16.0, 15.0));
    private static final VoxelShape COLUMN_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

    public SmokestackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STACKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STACKED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STACKED) ? COLUMN_SHAPE : CAPPED_SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(STACKED, joinsAbove(context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP || direction == Direction.DOWN) {
            return state.setValue(STACKED, joinsAbove(level, pos));
        }
        return state;
    }

    private static boolean isStack(BlockState state) {
        return state.getBlock() instanceof SmokestackBlock;
    }

    private static boolean joinsAbove(BlockGetter level, BlockPos pos) {
        return isStack(level.getBlockState(pos.above())) && heightBelow(level, pos) % MAX_HEIGHT != MAX_HEIGHT - 1;
    }

    private static int heightBelow(BlockGetter level, BlockPos pos) {
        int below = 0;
        while (isStack(level.getBlockState(pos.below(below + 1)))) {
            below++;
        }
        return below;
    }

    private static void refreshAbove(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        BlockPos at = pos.above();
        BlockState state = level.getBlockState(at);
        while (isStack(state)) {
            BlockState next = state.setValue(STACKED, joinsAbove(level, at));
            if (next != state) {
                level.setBlock(at, next, Block.UPDATE_CLIENTS);
            }
            at = at.above();
            state = level.getBlockState(at);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!isStack(newState)) {
            refreshAbove(level, pos);
            refreshColumn(level, pos.below());
            refreshColumn(level, pos.above());
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmokestackBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide) {
                int rgb = dye.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF;
                forEachInColumn(level, pos, be -> be.setColor(rgb));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        refreshAbove(level, pos);
        adoptColumnColor(level, pos);
        refreshColumn(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        refreshColumn(level, pos);
    }

    private static BlockPos columnBottom(BlockGetter level, BlockPos pos) {
        BlockPos at = pos;
        while (isStack(level.getBlockState(at.below()))) {
            at = at.below();
        }
        return at;
    }

    private static void forEachInColumn(Level level, BlockPos pos, Consumer<SmokestackBlockEntity> action) {
        if (!isStack(level.getBlockState(pos))) {
            return;
        }
        for (BlockPos at = columnBottom(level, pos); isStack(level.getBlockState(at)); at = at.above()) {
            if (level.getBlockEntity(at) instanceof SmokestackBlockEntity be) {
                action.accept(be);
            }
        }
    }

    private static void adoptColumnColor(Level level, BlockPos pos) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SmokestackBlockEntity self)) {
            return;
        }
        for (BlockPos near : new BlockPos[] {pos.below(), pos.above()}) {
            if (isStack(level.getBlockState(near)) && level.getBlockEntity(near) instanceof SmokestackBlockEntity be) {
                self.setColor(be.getColor());
                return;
            }
        }
    }

    public static void refreshColumn(Level level, BlockPos pos) {
        if (level.isClientSide || !isStack(level.getBlockState(pos))) {
            return;
        }
        BlockPos bottom = columnBottom(level, pos);
        int signal = 0;
        BlockPos top = bottom;
        for (BlockPos at = bottom; isStack(level.getBlockState(at)); at = at.above()) {
            if (!(level.getBlockEntity(at) instanceof SmokestackBlockEntity be)) {
                continue;
            }
            if (be.isBoilerDriven()) {
                return;
            }
            signal = Math.max(signal, level.getBestNeighborSignal(at));
            top = at;
        }
        for (BlockPos at = bottom; at.getY() <= top.getY(); at = at.above()) {
            if (level.getBlockEntity(at) instanceof SmokestackBlockEntity be) {
                be.setPower(at.equals(top) ? signal : 0);
            }
        }
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (!(be instanceof SmokestackBlockEntity stack)) return;
            if (lvl.isClientSide) {
                stack.clientTick();
            } else {
                stack.serverTick();
            }
        };
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
