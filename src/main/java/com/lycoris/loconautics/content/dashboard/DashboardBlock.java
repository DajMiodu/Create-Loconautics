package com.lycoris.loconautics.content.dashboard;

import com.lycoris.loconautics.client.connected.ConnectedBlock;
import com.lycoris.loconautics.client.connected.ConnectionBounds;
import com.lycoris.loconautics.common.DyedBlock;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelHost;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardBlock extends HorizontalDirectionalBlock
        implements ConnectedBlock, DyedBlock, IWrenchable, PanelHost, IBE<DashboardBlockEntity> {

    public static final int MAX_RUN = 5;

    public static final BooleanProperty JOIN_CW = BooleanProperty.create("join_cw");

    public static final BooleanProperty JOIN_CCW = BooleanProperty.create("join_ccw");

    private static final ConnectionBounds BOUNDS = new ConnectionBounds(MAX_RUN, 1, MAX_RUN);

    public static final float BOARD_TILT = -22.5f;

    public static final float BOARD_TOP = 14 / 16f;

    public static final float BOARD_DEPTH = 1 + 1 / 16f;

    private static final Map<Direction, VoxelShape> COLLISION = VoxelShapes.byHorizontalFacing(
            Shapes.or(box(0, 0, 4, 16, 2, 16), box(0, 2, 6, 16, 15, 16)));

    private static final Map<Direction, VoxelShape> BODY = VoxelShapes.byHorizontalFacing(
            Shapes.or(box(0, 0, 4, 16, 2, 16), box(0, 2, 8, 16, 8, 16)));

    private static final AABB BOARD_BOX = new AABB(0, -3 / 16f, 7 / 16f, 1, 0, 15.75 / 16f);

    private final DyeColor color;

    public DashboardBlock(BlockBehaviour.Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH)
                .setValue(JOIN_CW, false).setValue(JOIN_CCW, false));
    }

    @Override
    public DyeColor getColor() {
        return color;
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return DashboardBlocks.DASHBOARDS.get(dyed).get().defaultBlockState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(JOIN_CW, state.getValue(JOIN_CW))
                .setValue(JOIN_CCW, state.getValue(JOIN_CCW));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new DashboardBlock(properties, color));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, JOIN_CW, JOIN_CCW);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        int leftLength = freeEndLength(level, pos.relative(facing.getCounterClockWise()), facing, JOIN_CW);
        int rightLength = freeEndLength(level, pos.relative(facing.getClockWise()), facing, JOIN_CCW);
        boolean joinLeft = leftLength > 0 && leftLength < MAX_RUN;
        boolean joinRight = rightLength > 0 && (joinLeft ? leftLength + 1 : 1) + rightLength <= MAX_RUN;
        return defaultBlockState().setValue(FACING, facing)
                .setValue(JOIN_CCW, joinLeft).setValue(JOIN_CW, joinRight);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        List<AABB> tilted = new ArrayList<>();
        tilted.add(BOARD_BOX);

        PanelBlockEntity panel = PanelHost.panelAt(level, pos);
        if (panel != null) {
            for (PanelSlot slot : panel.slots()) {
                PanelPart part = panel.getPart(slot);
                if (part != null) tilted.add(part.type().localBox(slot.modelX, slot.modelZ));
            }
        }
        return TiltedShape.of(BODY.get(facing), tilted,
                point -> boardToBlock(facing, point), point -> blockToBoard(facing, point),
                outline(facing, tilted));
    }

    private static List<Vec3[]> outline(Direction facing, List<AABB> tilted) {
        AABB board = BOARD_BOX;
        Vec3[] profile = {
                profilePoint(facing, 0, 4 / 16.0), profilePoint(facing, 2 / 16.0, 4 / 16.0),
                profilePoint(facing, 2 / 16.0, 8 / 16.0),
                board(facing, board.minY, board.minZ), board(facing, board.maxY, board.minZ),
                board(facing, board.maxY, board.maxZ), board(facing, board.minY, board.maxZ),
                profilePoint(facing, 0, 1)
        };
        List<Vec3[]> lines = new ArrayList<>();
        for (int i = 0; i < profile.length; i++) {
            Vec3 a = profile[i];
            Vec3 b = profile[(i + 1) % profile.length];
            Vec3 shift = acrossStep(facing);
            lines.add(new Vec3[]{a, b});
            lines.add(new Vec3[]{a.add(shift), b.add(shift)});
            lines.add(new Vec3[]{a, a.add(shift)});
        }
        for (int i = 1; i < tilted.size(); i++) {
            Vec3[] corners = boxCorners(facing, tilted.get(i));
            int[][] pairs = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {5, 7}, {6, 7},
                    {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] pair : pairs) lines.add(new Vec3[]{corners[pair[0]], corners[pair[1]]});
        }
        return lines;
    }

    private static Vec3 acrossStep(Direction facing) {
        Direction across = facing.getClockWise();
        return new Vec3(across.getStepX(), 0, across.getStepZ());
    }

    private static Vec3 profilePoint(Direction facing, double height, double depth) {
        double[] local = new double[3];
        local[1] = height;
        place(local, facing.getOpposite(), depth);
        place(local, facing.getClockWise(), 0);
        return new Vec3(local[0], local[1], local[2]);
    }

    private static Vec3 board(Direction facing, double y, double z) {
        return boardToBlock(facing, new Vec3(0, y, z));
    }

    private static Vec3[] boxCorners(Direction facing, AABB box) {
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = boardToBlock(facing, new Vec3((i & 1) == 0 ? box.minX : box.maxX,
                    (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ));
        }
        return corners;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return COLLISION.get(state.getValue(FACING));
    }

    private static Vec3 boardToBlock(Direction facing, Vec3 board) {
        double sin = Math.sin(Math.toRadians(BOARD_TILT));
        double cos = Math.cos(Math.toRadians(BOARD_TILT));
        double depth = board.z - BOARD_DEPTH;
        double modelY = board.y * cos - depth * sin + BOARD_TOP;
        double modelZ = board.y * sin + depth * cos + 1;

        Direction across = facing.getClockWise();
        double[] local = new double[3];
        local[1] = modelY;
        place(local, across, board.x);
        place(local, facing.getOpposite(), modelZ);
        return new Vec3(local[0], local[1], local[2]);
    }

    private static Vec3 blockToBoard(Direction facing, Vec3 local) {
        double modelX = modelCoord(local, facing.getClockWise());
        double modelZ = modelCoord(local, facing.getOpposite());
        double sin = Math.sin(Math.toRadians(-BOARD_TILT));
        double cos = Math.cos(Math.toRadians(-BOARD_TILT));
        double height = local.y - BOARD_TOP;
        double depth = modelZ - 1;
        return new Vec3(modelX, height * cos - depth * sin, height * sin + depth * cos + BOARD_DEPTH);
    }

    private static void place(double[] local, Direction axis, double model) {
        int index = axis.getAxis() == Direction.Axis.X ? 0 : 2;
        local[index] = axis.getAxisDirection() == Direction.AxisDirection.POSITIVE ? model : 1 - model;
    }

    @Override
    public ConnectionBounds connectionBounds() {
        return BOUNDS;
    }

    @Override
    public BlockPos groupAnchor(BlockGetter level, BlockState state, BlockPos pos) {
        return runAnchor(level, state, pos);
    }

    @Override
    public boolean connectsTo(BlockGetter level, BlockState state, BlockPos anchor, BlockPos neighborPos) {
        BlockState neighbor = level.getBlockState(neighborPos);
        if (!neighbor.is(this) || neighbor.getValue(FACING) != state.getValue(FACING)) return false;
        return anchor.equals(runAnchor(level, neighbor, neighborPos));
    }

    private BlockPos runAnchor(BlockGetter level, BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction back = facing.getCounterClockWise();
        BlockPos anchor = pos;
        BlockState current = state;
        for (int i = 0; i < MAX_RUN && current.getValue(JOIN_CCW); i++) {
            BlockPos previous = anchor.relative(back);
            BlockState previousState = level.getBlockState(previous);
            if (!matches(previousState, facing) || !previousState.getValue(JOIN_CW)) break;
            anchor = previous;
            current = previousState;
        }
        return anchor.immutable();
    }

    private int runLength(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos at = runAnchor(level, state, pos);
        BlockState current = level.getBlockState(at);
        int length = 1;
        while (length < MAX_RUN && current.getValue(JOIN_CW)) {
            at = at.relative(facing.getClockWise());
            current = level.getBlockState(at);
            if (!matches(current, facing) || !current.getValue(JOIN_CCW)) break;
            length++;
        }
        return length;
    }

    private int freeEndLength(BlockGetter level, BlockPos pos, Direction facing, BooleanProperty towardUs) {
        BlockState state = level.getBlockState(pos);
        if (!matches(state, facing) || state.getValue(towardUs)) return 0;
        return runLength(level, pos, state);
    }

    private boolean matches(BlockState state, Direction facing) {
        return state.is(this) && state.getValue(FACING) == facing;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult painted = paint(stack, state, level, pos, player);
        if (painted != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return painted;

        ItemInteractionResult result = configureTargetedPart(stack, state, level, pos, player, hit);
        if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return result;
        result = labelTargetedPart(stack, state, level, pos, player, hit);
        if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return result;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return useTargetedPart(state, level, pos, player, hit);
    }

    @Override
    public PanelSlot targetedSlot(BlockPos pos, BlockState state, Vec3 clickLocation) {
        Vec3 local = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
        return modelCoord(local, state.getValue(FACING).getClockWise()) < 0.5
                ? PanelSlot.TOP_RIGHT : PanelSlot.TOP_LEFT;
    }

    private static double modelCoord(Vec3 local, Direction axis) {
        double along = axis.getAxis() == Direction.Axis.X ? local.x : local.z;
        return axis.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1 - along : along;
    }

    @Nullable
    private static PanelSlot aimedPart(BlockState state, BlockPos pos, PanelBlockEntity panel, Vec3 hit) {
        Vec3 board = blockToBoard(state.getValue(FACING), hit.subtract(Vec3.atLowerCornerOf(pos)));
        for (PanelSlot slot : panel.slots()) {
            PanelPart part = panel.getPart(slot);
            if (part == null) continue;
            if (part.type().localBox(slot.modelX, slot.modelZ).inflate(1 / 512.0).contains(board)) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public boolean removedWhenEmpty() {
        return false;
    }

    @Override
    public boolean supportsCenteredPart() {
        return false;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        PanelBlockEntity panel = PanelHost.panelAt(level, pos);
        if (panel != null && target instanceof BlockHitResult hit) {
            PanelSlot aimed = aimedPart(state, pos, panel, hit.getLocation());
            ItemStack stack = aimed == null ? ItemStack.EMPTY : panel.stackForSlot(aimed);
            if (!stack.isEmpty()) {
                return player != null && player.isShiftKeyDown() ? stack : new ItemStack(stack.getItem());
            }
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        PanelBlockEntity panel = PanelHost.panelAt(level, pos);
        if (panel == null || panel.partCount() == 0) {
            return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        }

        PanelSlot aimed = player.pick(10.0, 0, false) instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)
                ? aimedPart(state, pos, panel, hit.getLocation()) : null;
        if (aimed == null) {
            dropAllParts(level, pos, panel, player);
            return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        }
        if (!level.isClientSide) {
            ItemStack stack = panel.stackForSlot(aimed);
            panel.removePart(aimed);
            if (!player.isCreative()) Block.popResource(level, pos, stack);
        }
        return false;
    }

    private static void dropAllParts(Level level, BlockPos pos, PanelBlockEntity panel, Player player) {
        if (level.isClientSide) return;
        for (PanelSlot slot : panel.slots()) {
            if (!panel.hasPart(slot)) continue;
            ItemStack stack = panel.stackForSlot(slot);
            panel.removePart(slot);
            if (!player.isCreative()) Block.popResource(level, pos, stack);
        }
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        PanelBlockEntity panel = PanelHost.panelAt(context.getLevel(), context.getClickedPos());
        if (panel == null || panel.partCount() == 0) {
            return IWrenchable.super.onSneakWrenched(state, context);
        }
        return takeTargetedPartWithWrench(state, context);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> parts = PanelHost.partDrops(params);
        if (parts == null) return super.getDrops(state, params);
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        drops.addAll(parts);
        return drops;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !(newState.getBlock() instanceof DashboardBlock)) {
            PanelBlockEntity panel = PanelHost.panelAt(level, pos);
            if (panel != null) panel.releaseParts();
        }
        if (!level.isClientSide && !(newState.getBlock() instanceof DashboardBlock)) {
            setNeighborJoin(level, pos, state, false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        refreshRun(level, state, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) setNeighborJoin(level, pos, state, true);
        refreshRun(level, state, pos);
    }

    private void setNeighborJoin(Level level, BlockPos pos, BlockState state, boolean joined) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(JOIN_CCW)) setJoin(level, pos.relative(facing.getCounterClockWise()), facing, JOIN_CW, joined);
        if (state.getValue(JOIN_CW)) setJoin(level, pos.relative(facing.getClockWise()), facing, JOIN_CCW, joined);
    }

    private void setJoin(Level level, BlockPos pos, Direction facing, BooleanProperty property, boolean joined) {
        BlockState neighbor = level.getBlockState(pos);
        if (!matches(neighbor, facing) || neighbor.getValue(property) == joined) return;
        level.setBlock(pos, neighbor.setValue(property, joined), Block.UPDATE_ALL);
    }

    private void refreshRun(Level level, BlockState state, BlockPos pos) {
        if (!level.isClientSide) return;
        Direction facing = state.getValue(FACING);
        for (int offset = -MAX_RUN; offset <= MAX_RUN; offset++) {
            BlockPos at = pos.relative(facing.getClockWise(), offset);
            BlockState atState = level.getBlockState(at);
            if (matches(atState, facing)) level.sendBlockUpdated(at, atState, atState, Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    public Class<DashboardBlockEntity> getBlockEntityClass() {
        return DashboardBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DashboardBlockEntity> getBlockEntityType() {
        return DashboardBlocks.DASHBOARD_BE.get();
    }
}
