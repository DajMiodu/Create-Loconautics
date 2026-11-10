package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.common.DyedBlock;
import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class BufferBlock extends HorizontalDirectionalBlock
        implements IBE<BufferBlockEntity>, DyedBlock, IWrenchable, BlockSubLevelCollisionShape {

    private static final double HEAD_ROLL = 45.0;
    private static final double HEAD_CENTER_Y = 10.0 / 16.0;

    private static final Map<BufferSize, Map<CopycatPlacement, Map<Direction, VoxelShape>>> SHAPES =
            new EnumMap<>(BufferSize.class);
    private static final Map<BufferSize, Map<CopycatPlacement, Map<Direction, VoxelShape>>> COLLISION =
            new EnumMap<>(BufferSize.class);
    private static final Map<CopycatPlacement, Map<Direction, VoxelShape>> BODY_COLLIDER =
            new EnumMap<>(CopycatPlacement.class);

    static {
        for (CopycatPlacement placement : CopycatPlacement.values()) {
            Map<Direction, VoxelShape> plates = VoxelShapes.byHorizontalFacing(plate(placement));
            Map<Direction, VoxelShape> clipped = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                clipped.put(facing, Shapes.join(plates.get(facing), Shapes.block(), BooleanOp.AND).optimize());
            }
            BODY_COLLIDER.put(placement, clipped);
        }
        for (BufferSize size : BufferSize.values()) {
            Map<CopycatPlacement, Map<Direction, VoxelShape>> shapes = new EnumMap<>(CopycatPlacement.class);
            Map<CopycatPlacement, Map<Direction, VoxelShape>> collision = new EnumMap<>(CopycatPlacement.class);
            for (CopycatPlacement placement : CopycatPlacement.values()) {
                Map<Direction, VoxelShape> body = VoxelShapes.byHorizontalFacing(body(size, placement));
                Map<Direction, VoxelShape> head = VoxelShapes.byHorizontalFacing(head(size, placement));
                Map<Direction, VoxelShape> tilted = new EnumMap<>(Direction.class);
                Map<Direction, VoxelShape> clipped = new EnumMap<>(Direction.class);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    tilted.put(facing, tiltedHead(body.get(facing), head.get(facing), facing, placement));
                    clipped.put(facing, Shapes.join(Shapes.or(body.get(facing), diamondBounds(head.get(facing), facing, placement)),
                            Shapes.block(), BooleanOp.AND).optimize());
                }
                shapes.put(placement, tilted);
                collision.put(placement, clipped);
            }
            SHAPES.put(size, shapes);
            COLLISION.put(size, collision);
        }
    }

    private static VoxelShape plate(CopycatPlacement placement) {
        return Block.box(0, 4, 13, 16, 16, 16).move(0.0, placement.blocks(), 0.0);
    }

    private static VoxelShape body(BufferSize size, CopycatPlacement placement) {
        int out = size.extensionPixels();
        int sleeveOut = size.sleeveOutPixels();
        return Shapes.or(plate(placement),
                Block.box(5, 7, 10 - sleeveOut, 11, 13, 16).move(0.0, placement.blocks(), 0.0),
                Block.box(6, 8, 10 - out, 10, 12, 16).move(0.0, placement.blocks(), 0.0));
    }

    private static VoxelShape head(BufferSize size, CopycatPlacement placement) {
        int out = size.extensionPixels();
        return Block.box(4, 6, 8 - out, 12, 14, 10 - out).move(0.0, placement.blocks(), 0.0);
    }

    private static VoxelShape tiltedHead(VoxelShape body, VoxelShape head, Direction facing, CopycatPlacement placement) {
        double centerY = HEAD_CENTER_Y + placement.blocks();
        return TiltedShape.of(body, head.toAabbs(),
                point -> roll(point, facing, centerY, HEAD_ROLL),
                point -> roll(point, facing, centerY, -HEAD_ROLL));
    }

    private static VoxelShape diamondBounds(VoxelShape head, Direction facing, CopycatPlacement placement) {
        double centerY = HEAD_CENTER_Y + placement.blocks();
        VoxelShape bounds = Shapes.empty();
        for (AABB box : head.toAabbs()) {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (Vec3 corner : new Vec3[]{new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                    new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                    new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                    new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ)}) {
                Vec3 turned = roll(corner, facing, centerY, HEAD_ROLL);
                minX = Math.min(minX, turned.x);
                minY = Math.min(minY, turned.y);
                minZ = Math.min(minZ, turned.z);
                maxX = Math.max(maxX, turned.x);
                maxY = Math.max(maxY, turned.y);
                maxZ = Math.max(maxZ, turned.z);
            }
            bounds = Shapes.or(bounds, Shapes.box(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return bounds;
    }

    private static Vec3 roll(Vec3 point, Direction facing, double centerY, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double dy = point.y - centerY;
        if (facing.getAxis() == Direction.Axis.Z) {
            double dx = point.x - 0.5;
            return new Vec3(0.5 + dx * cos - dy * sin, centerY + dx * sin + dy * cos, point.z);
        }
        double dz = point.z - 0.5;
        return new Vec3(point.x, centerY + dz * sin + dy * cos, 0.5 + dz * cos - dy * sin);
    }

    private final DyeColor color;

    public BufferBlock(Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BufferSize.SIZE, BufferSize.HALF)
                .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.TOP));
    }

    @Override
    public DyeColor getColor() {
        return color;
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return BufferBlocks.BUFFERS.get(dyed).get().defaultBlockState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(BufferSize.SIZE, state.getValue(BufferSize.SIZE))
                .setValue(CopycatPlacement.PLACEMENT, state.getValue(CopycatPlacement.PLACEMENT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new BufferBlock(properties, color));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BufferSize.SIZE, CopycatPlacement.PLACEMENT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, ShiftPlacement.facing(context))
                .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.aimedAt(context));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(BufferSize.SIZE)).get(state.getValue(CopycatPlacement.PLACEMENT))
                .get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION.get(state.getValue(BufferSize.SIZE)).get(state.getValue(CopycatPlacement.PLACEMENT))
                .get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return BODY_COLLIDER.get(state.getValue(CopycatPlacement.PLACEMENT)).get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return CopycatBlockLogic.hasSupportBehind(level, pos, state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult painted = paint(stack, state, level, pos, player);
        if (painted != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return painted;
        }
        return CopycatBlockLogic.useItemOn(stack, level, pos, player, hit.getDirection());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CopycatBlockLogic.placedBy(level, pos, placer);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(context.getClickedPos(), state.setValue(BufferSize.SIZE, state.getValue(BufferSize.SIZE).next()), 3);
        level.playSound(null, context.getClickedPos(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        InteractionResult reclaimed = CopycatBlockLogic.reclaim(state, context.getLevel(),
                context.getClickedPos(), context.getPlayer());
        if (reclaimed != InteractionResult.PASS) {
            return reclaimed;
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !(newState.getBlock() instanceof BufferBlock) && !movedByPiston) {
            CopycatBlockLogic.popConsumed(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<BufferBlockEntity> getBlockEntityClass() {
        return BufferBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BufferBlockEntity> getBlockEntityType() {
        return BufferBlocks.BUFFER_BE.get();
    }
}
