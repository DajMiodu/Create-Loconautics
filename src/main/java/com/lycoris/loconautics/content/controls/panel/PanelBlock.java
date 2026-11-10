package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PanelBlock extends FaceAttachedHorizontalDirectionalBlock
        implements IWrenchable, PanelHost, BlockSubLevelAssemblyListener, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final double[] FLAT_SLOT_BOX = {1, 0, 1, 7, 2, 7};

    public static final double CENTER_OFFSET = 4;

    private static final VoxelShape[] FULL = new VoxelShape[Direction.values().length];

    private static final VoxelShape[][][] FLAT_SHAPES = buildShapes(FLAT_SLOT_BOX);

    static {
        FULL[Direction.DOWN.ordinal()] = Block.box(0, 0, 0, 16, 2, 16);
        FULL[Direction.UP.ordinal()] = Block.box(0, 14, 0, 16, 16, 16);
        FULL[Direction.NORTH.ordinal()] = Block.box(0, 0, 0, 16, 16, 2);
        FULL[Direction.SOUTH.ordinal()] = Block.box(0, 0, 14, 16, 16, 16);
        FULL[Direction.WEST.ordinal()] = Block.box(0, 0, 0, 2, 16, 16);
        FULL[Direction.EAST.ordinal()] = Block.box(14, 0, 0, 16, 16, 16);
    }

    public static VoxelShape[][][] buildShapes(double[] slotBox) {
        return buildShapes(slotBox, false);
    }

    public static VoxelShape[][][] buildShapes(double[] slotBox, boolean centered) {
        VoxelShape[][][] shapes =
                new VoxelShape[AttachFace.values().length][4][PanelSlot.values().length];

        int[] faceRot = {0, 0, 0};
        faceRot[AttachFace.FLOOR.ordinal()] = 0;
        faceRot[AttachFace.WALL.ordinal()] = 90;
        faceRot[AttachFace.CEILING.ordinal()] = 180;

        for (AttachFace face : AttachFace.values()) {
            for (int facingIndex = 0; facingIndex < 4; facingIndex++) {
                int ceiling = face == AttachFace.CEILING ? 2 : 0;
                int yaw = ((facingIndex + ceiling) % 4) * 90;
                for (PanelSlot slot : PanelSlot.values()) {
                    double offX = centered ? CENTER_OFFSET : slot.modelX;
                    double offZ = centered ? CENTER_OFFSET : slot.modelZ;
                    double[] box = {
                            slotBox[0] + offX, slotBox[1], slotBox[2] + offZ,
                            slotBox[3] + offX, slotBox[4], slotBox[5] + offZ,
                    };
                    box = rotateX(box, faceRot[face.ordinal()]);
                    box = rotateY(box, yaw);
                    shapes[face.ordinal()][facingIndex][slot.ordinal()] =
                            Block.box(box[0], box[1], box[2], box[3], box[4], box[5]);
                }
            }
        }
        return shapes;
    }

    private static int yawIndex(Direction facing) {
        return (facing.get2DDataValue() + 2) % 4;
    }

    private static double[] rotateX(double[] b, int degrees) {
        double[] out = b;
        for (int i = 0; i < degrees / 90; i++) {
            out = normalize(new double[]{out[0], out[2], 16 - out[1], out[3], out[5], 16 - out[4]});
        }
        return out;
    }

    private static double[] rotateY(double[] b, int degrees) {
        double[] out = b;
        for (int i = 0; i < degrees / 90; i++) {
            out = normalize(new double[]{16 - out[2], out[1], out[0], 16 - out[5], out[4], out[3]});
        }
        return out;
    }

    private static double[] normalize(double[] b) {
        return new double[]{
                Math.min(b[0], b[3]), Math.min(b[1], b[4]), Math.min(b[2], b[5]),
                Math.max(b[0], b[3]), Math.max(b[1], b[4]), Math.max(b[2], b[5]),
        };
    }

    protected PanelBlock(Properties properties) {
        super(properties);
        BlockState state = defaultBlockState().setValue(WATERLOGGED, false);
        for (BooleanProperty slot : PanelSlot.PROPERTIES) {
            state = state.setValue(slot, false);
        }
        registerDefaultState(state);
    }

    @Nullable
    protected static PanelBlockEntity panelAt(BlockGetter level, BlockPos pos) {
        return PanelHost.panelAt(level, pos);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        if (resultingLevel.getBlockEntity(newPos) instanceof PanelBlockEntity panel) {
            panel.onAssembled(newPos.subtract(oldPos));
        }
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACE, FACING));
        builder.add(WATERLOGGED);
        builder.add(PanelSlot.PROPERTIES);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = getConnectedDirection(state).getOpposite();
        BlockPos target = pos.relative(direction);
        return level.getBlockState(target).isFaceSturdy(level, target, direction.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placement = super.getStateForPlacement(context);
        if (placement == null) return null;
        placement = placement.setValue(WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        if (placement.getValue(FACE) == AttachFace.FLOOR) {
            placement = placement.setValue(FACING, placement.getValue(FACING).getOpposite());
        }
        return placement;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
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
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        ItemStack stack = targetedPartStack(state, target, level, pos, player);
        return stack.isEmpty() ? super.getCloneItemStack(state, target, level, pos, player) : stack;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (takeTargetedPartOnBreak(state, level, pos, player)) return false;
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return IWrenchable.super.onWrenched(state, context);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return takeTargetedPartWithWrench(state, context);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = PanelHost.partDrops(params);
        return drops == null ? super.getDrops(state, params) : drops;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PanelBlockEntity panel = panelAt(level, pos);
            if (panel != null) panel.releaseParts();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        AttachFace face = state.getValue(FACE);
        int facingIndex = yawIndex(state.getValue(FACING));
        PanelBlockEntity panel = panelAt(level, pos);
        VoxelShape shape = null;
        for (PanelSlot slot : PanelSlot.values()) {
            if (!state.getValue(slot.property())) continue;
            PanelPart held = panel == null ? null : panel.getPart(slot);
            boolean centered = panel != null && panel.isCentered();
            VoxelShape[][][] shapes = held == null ? FLAT_SHAPES
                    : centered ? held.type().centeredShapes() : held.type().shapes();
            VoxelShape part = shapes[face.ordinal()][facingIndex][slot.ordinal()];
            shape = shape == null ? part : Shapes.or(shape, part);
        }
        return shape != null ? shape : FULL[getConnectedDirection(state).getOpposite().ordinal()];
    }

    @Override
    public PanelSlot targetedSlot(BlockPos pos, BlockState state, Vec3 clickLocation) {
        return getTargetedSlot(pos, state, clickLocation);
    }

    public static boolean mountedOn(BlockState state, Direction face) {
        return getConnectedDirection(state) == face;
    }

    public static boolean aimedAtCenter(BlockPos pos, BlockState state, Vec3 clickLocation) {
        Vec3 localClick = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
        Vec3 vec = VecHelper.rotateCentered(new Vec3(.5, 0, .5), 180, Axis.Y);
        vec = VecHelper.rotateCentered(vec, Mth.RAD_TO_DEG * getXRot(state) + 90, Axis.X);
        vec = VecHelper.rotateCentered(vec, Mth.RAD_TO_DEG * getYRot(state), Axis.Y);
        return vec.distanceToSqr(localClick) < 0.0324;
    }

    public static PanelSlot getTargetedSlot(BlockPos pos, BlockState state, Vec3 clickLocation) {
        double bestDistance = Double.MAX_VALUE;
        PanelSlot bestSlot = PanelSlot.BOTTOM_LEFT;
        Vec3 localClick = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
        float xRot = Mth.RAD_TO_DEG * getXRot(state);
        float yRot = Mth.RAD_TO_DEG * getYRot(state);

        for (PanelSlot slot : PanelSlot.values()) {
            Vec3 vec = new Vec3(.25 + slot.xOffset * .5, 0, .25 + slot.yOffset * .5);
            vec = VecHelper.rotateCentered(vec, 180, Axis.Y);
            vec = VecHelper.rotateCentered(vec, xRot + 90, Axis.X);
            vec = VecHelper.rotateCentered(vec, yRot, Axis.Y);

            double diff = vec.distanceToSqr(localClick);
            if (diff > bestDistance) continue;
            bestDistance = diff;
            bestSlot = slot;
        }
        return bestSlot;
    }

    public static float getXRot(BlockState state) {
        AttachFace face = state.getOptionalValue(FACE).orElse(AttachFace.FLOOR);
        return face == AttachFace.CEILING ? Mth.PI / 2 : face == AttachFace.FLOOR ? -Mth.PI / 2 : 0;
    }

    public static float getYRot(BlockState state) {
        Direction facing = state.getOptionalValue(FACING).orElse(Direction.SOUTH);
        AttachFace face = state.getOptionalValue(FACE).orElse(AttachFace.FLOOR);
        return (face == AttachFace.CEILING ? Mth.PI : 0)
                + AngleHelper.rad(AngleHelper.horizontalAngle(facing));
    }
}
