package com.lycoris.loconautics.content.boiler.barrel;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.placement.PoleHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class BoilerBarrelBlock extends DirectionalBlock
        implements IWrenchable, EntityBlock, BlockSubLevelCollisionShape {

    public static final BooleanProperty CONNECTED_POS = BooleanProperty.create("connected_pos");
    public static final BooleanProperty CONNECTED_NEG = BooleanProperty.create("connected_neg");
    public static final BooleanProperty WRENCHED_POS = BooleanProperty.create("wrenched_pos");
    public static final BooleanProperty WRENCHED_NEG = BooleanProperty.create("wrenched_neg");
    public static final BooleanProperty SMOKE_POS = BooleanProperty.create("smoke_pos");
    public static final BooleanProperty SMOKE_NEG = BooleanProperty.create("smoke_neg");

    private static final int SMALL_HELPER_ID = PlacementHelpers.register(new BarrelPlacementHelper(false));
    private static final int BIG_HELPER_ID = PlacementHelpers.register(new BarrelPlacementHelper(true));

    private static final double[] SMALL = {-8.0, 24.0, 1.373, 14.629};
    private static final double[] BIG = {-16.0, 32.0, -0.532, 16.532};
    private static final int CORNER_STEPS = 3;

    private static final double SQRT_HALF = Math.sqrt(0.5);

    private static final VoxelShape[][] VISUAL_SHAPES = buildAll(BoilerBarrelBlock::buildVisualBarrel);
    private static final VoxelShape[][] COLLISION_SHAPES = buildAll(BoilerBarrelBlock::buildSteppedBarrel);

    private static VoxelShape[][] buildAll(BiFunction<Direction.Axis, double[], VoxelShape> build) {
        VoxelShape[][] out = new VoxelShape[2][3];
        for (int b = 0; b < 2; b++) {
            double[] oct = b == 1 ? BIG : SMALL;
            for (Direction.Axis axis : Direction.Axis.values()) {
                out[b][axis.ordinal()] = build.apply(axis, oct);
            }
        }
        return out;
    }

    private static VoxelShape buildVisualBarrel(Direction.Axis lengthAxis, double[] oct) {
        double outLo = oct[0], outHi = oct[1], flatLo = oct[2], flatHi = oct[3];
        VoxelShape straight = Shapes.or(boxForAxis(lengthAxis, outLo, outHi, flatLo, flatHi),
                boxForAxis(lengthAxis, flatLo, flatHi, outLo, outHi)).optimize();

        double half = (outHi - outLo) / 32.0;
        double flat = (flatHi - flatLo) / 32.0;
        double along = (half + flat) * SQRT_HALF;
        double across = (half - flat) * SQRT_HALF;
        List<AABB> turned = List.of(turnedBox(lengthAxis, -along, along, -across, across),
                turnedBox(lengthAxis, -across, across, -along, along));

        return TiltedShape.of(straight, turned,
                point -> turnedToBlock(lengthAxis, point), point -> blockToTurned(lengthAxis, point),
                octagonOutline(lengthAxis, half, flat));
    }

    private static List<Vec3[]> octagonOutline(Direction.Axis lengthAxis, double half, double flat) {
        double[][] rim = {{half, flat}, {flat, half}, {-flat, half}, {-half, flat},
                {-half, -flat}, {-flat, -half}, {flat, -half}, {half, -flat}};
        List<Vec3[]> edges = new ArrayList<>();
        for (int i = 0; i < rim.length; i++) {
            double[] here = rim[i];
            double[] next = rim[(i + 1) % rim.length];
            Vec3 lo = rimPoint(lengthAxis, here, 0.0);
            Vec3 hi = rimPoint(lengthAxis, here, 1.0);
            edges.add(new Vec3[]{lo, hi});
            edges.add(new Vec3[]{lo, rimPoint(lengthAxis, next, 0.0)});
            edges.add(new Vec3[]{hi, rimPoint(lengthAxis, next, 1.0)});
        }
        return edges;
    }

    private static Vec3 rimPoint(Direction.Axis lengthAxis, double[] cross, double along) {
        double u = cross[0] + 0.5;
        double v = cross[1] + 0.5;
        return switch (lengthAxis) {
            case X -> new Vec3(along, u, v);
            case Y -> new Vec3(u, along, v);
            case Z -> new Vec3(u, v, along);
        };
    }

    private static AABB turnedBox(Direction.Axis lengthAxis, double u0, double u1, double v0, double v1) {
        return switch (lengthAxis) {
            case X -> new AABB(0.0, u0, v0, 1.0, u1, v1);
            case Y -> new AABB(u0, 0.0, v0, u1, 1.0, v1);
            case Z -> new AABB(u0, v0, 0.0, u1, v1, 1.0);
        };
    }

    private static Vec3 turnedToBlock(Direction.Axis lengthAxis, Vec3 turned) {
        double s = crossU(lengthAxis, turned);
        double t = crossV(lengthAxis, turned);
        return withCross(lengthAxis, turned, (s - t) * SQRT_HALF + 0.5, (s + t) * SQRT_HALF + 0.5);
    }

    private static Vec3 blockToTurned(Direction.Axis lengthAxis, Vec3 local) {
        double u = crossU(lengthAxis, local) - 0.5;
        double v = crossV(lengthAxis, local) - 0.5;
        return withCross(lengthAxis, local, (u + v) * SQRT_HALF, (v - u) * SQRT_HALF);
    }

    private static double crossU(Direction.Axis lengthAxis, Vec3 point) {
        return lengthAxis == Direction.Axis.X ? point.y : point.x;
    }

    private static double crossV(Direction.Axis lengthAxis, Vec3 point) {
        return lengthAxis == Direction.Axis.Z ? point.y : point.z;
    }

    private static Vec3 withCross(Direction.Axis lengthAxis, Vec3 point, double u, double v) {
        return switch (lengthAxis) {
            case X -> new Vec3(point.x, u, v);
            case Y -> new Vec3(u, point.y, v);
            case Z -> new Vec3(u, v, point.z);
        };
    }

    private static VoxelShape buildSteppedBarrel(Direction.Axis lengthAxis, double[] oct) {
        double outLo = oct[0], outHi = oct[1], flatLo = oct[2], flatHi = oct[3];
        double step = (flatLo - outLo) / CORNER_STEPS;
        VoxelShape shape = Shapes.or(boxForAxis(lengthAxis, outLo, outHi, flatLo, flatHi),
                boxForAxis(lengthAxis, flatLo, flatHi, outLo, outHi));
        for (int k = 0; k < CORNER_STEPS; k++) {
            double inset = (k + 1) * step;
            double u0 = outLo + inset;
            double u1 = outHi - inset;
            shape = Shapes.or(shape,
                    boxForAxis(lengthAxis, u0, u1, flatHi + k * step, flatHi + (k + 1) * step));
            shape = Shapes.or(shape,
                    boxForAxis(lengthAxis, u0, u1, flatLo - (k + 1) * step, flatLo - k * step));
        }
        return shape.optimize();
    }

    private static VoxelShape boxForAxis(Direction.Axis lengthAxis, double u0, double u1, double v0, double v1) {
        return switch (lengthAxis) {
            case X -> Block.box(0.0, u0, v0, 16.0, u1, v1);
            case Y -> Block.box(u0, 0.0, v0, u1, 16.0, v1);
            case Z -> Block.box(u0, v0, 0.0, u1, v1, 16.0);
        };
    }

    private VoxelShape shapeFor(BlockState state, VoxelShape[][] shapes) {
        return shapes[big ? 1 : 0][state.getValue(FACING).getAxis().ordinal()];
    }

    static VoxelShape bigOctagon(Direction.Axis axis) {
        return VISUAL_SHAPES[1][axis.ordinal()];
    }

    public static final MapCodec<BoilerBarrelBlock> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            propertiesCodec(),
            Codec.STRING.fieldOf("color").forGetter(b -> b.color),
            Codec.BOOL.fieldOf("big").forGetter(b -> b.big)
    ).apply(inst, BoilerBarrelBlock::new));

    public final String color;
    public final boolean big;

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    public BoilerBarrelBlock(Properties properties, String color, boolean big) {
        super(properties);
        this.color = color;
        this.big = big;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTED_POS, false).setValue(CONNECTED_NEG, false)
                .setValue(WRENCHED_POS, false).setValue(WRENCHED_NEG, false)
                .setValue(SMOKE_POS, false).setValue(SMOKE_NEG, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED_POS, CONNECTED_NEG, WRENCHED_POS, WRENCHED_NEG, SMOKE_POS, SMOKE_NEG);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(state, VISUAL_SHAPES);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(state, COLLISION_SHAPES);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return shapeFor(state, COLLISION_SHAPES);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    private boolean sameFamily(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof BoilerBarrelBlock other
                && other.big == this.big
                && s.getValue(FACING).getAxis() == axis;
    }

    private boolean autoJoins(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        return sameFamily(level, pos, axis) && level.getBlockState(pos).getBlock() == this;
    }

    static Direction positiveOf(Direction.Axis axis) {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
    }

    static BooleanProperty connectedFor(Direction.Axis axis, Direction toNeighbor) {
        return toNeighbor == positiveOf(axis) ? CONNECTED_POS : CONNECTED_NEG;
    }

    static BooleanProperty wrenchedFor(Direction.Axis axis, Direction toNeighbor) {
        return toNeighbor == positiveOf(axis) ? WRENCHED_POS : WRENCHED_NEG;
    }

    static BooleanProperty smokeEndFor(Direction.Axis axis, Direction toEnd) {
        return toEnd == positiveOf(axis) ? SMOKE_POS : SMOKE_NEG;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return turnEnds(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Rotation turn = mirror.getRotation(state.getValue(FACING));
        return turnEnds(state, turn::rotate);
    }

    // the end flags are tied to the positive and negative end of the axis, so a turn can swap them
    private static BlockState turnEnds(BlockState state, UnaryOperator<Direction> turn) {
        Direction facing = state.getValue(FACING);
        boolean swapped = turn.apply(positiveOf(facing.getAxis())).getAxisDirection() != Direction.AxisDirection.POSITIVE;
        return state.setValue(FACING, turn.apply(facing))
                .setValue(CONNECTED_POS, state.getValue(swapped ? CONNECTED_NEG : CONNECTED_POS))
                .setValue(CONNECTED_NEG, state.getValue(swapped ? CONNECTED_POS : CONNECTED_NEG))
                .setValue(WRENCHED_POS, state.getValue(swapped ? WRENCHED_NEG : WRENCHED_POS))
                .setValue(WRENCHED_NEG, state.getValue(swapped ? WRENCHED_POS : WRENCHED_NEG))
                .setValue(SMOKE_POS, state.getValue(swapped ? SMOKE_NEG : SMOKE_POS))
                .setValue(SMOKE_NEG, state.getValue(swapped ? SMOKE_POS : SMOKE_NEG));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getNearestLookingDirection();
        Direction facing = look.getOpposite();
        Direction.Axis axis = facing.getAxis();
        BlockState state = defaultBlockState().setValue(FACING, facing)
                .setValue(smokeEndFor(axis, look), true);
        for (Direction end : new Direction[]{positiveOf(axis), positiveOf(axis).getOpposite()}) {
            boolean join = autoJoins(context.getLevel(), context.getClickedPos().relative(end), axis);
            state = state.setValue(connectedFor(axis, end), join);
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        if (direction.getAxis() != axis) {
            return state;
        }
        BooleanProperty connected = connectedFor(axis, direction);
        BooleanProperty wrenched = wrenchedFor(axis, direction);
        if (!sameFamily(level, neighborPos, axis)) {
            return state.setValue(connected, false).setValue(wrenched, false);
        }
        return state.setValue(connected, autoJoins(level, neighborPos, axis) != state.getValue(wrenched));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBarrelBlockEntity(BoilerBlocks.BOILER_BARREL_BE.get(), pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        IPlacementHelper helper = PlacementHelpers.get(big ? BIG_HELPER_ID : SMALL_HELPER_ID);
        if (!helper.matchesItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return helper.getOffset(player, level, state, pos, hit)
                .placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        Direction clicked = context.getClickedFace();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (clicked.getAxis() == axis) {
            if (!level.isClientSide()) {
                BooleanProperty smoke = smokeEndFor(axis, clicked);
                level.setBlock(pos, state.cycle(smoke), Block.UPDATE_CLIENTS);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        Direction end = aimedEnd(state, context);
        BlockPos neighborPos = pos.relative(end);
        if (!sameFamily(level, neighborPos, axis)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            boolean connect = !state.getValue(connectedFor(axis, end));
            toggleSeam(level, pos, neighborPos, axis, end, connect);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private Direction aimedEnd(BlockState state, UseOnContext context) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        Vec3 rel = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
        double comp = axis.choose(rel.x, rel.y, rel.z);
        Direction pos = positiveOf(axis);
        return comp >= 0 ? pos : pos.getOpposite();
    }

    private void toggleSeam(Level level, BlockPos pos, BlockPos neighborPos, Direction.Axis axis, Direction end, boolean connect) {
        boolean wrenched = connect != autoJoins(level, neighborPos, axis);
        BlockState here = level.getBlockState(pos);
        BlockState there = level.getBlockState(neighborPos);
        Direction back = end.getOpposite();

        level.setBlock(pos, here
                .setValue(connectedFor(axis, end), connect)
                .setValue(wrenchedFor(axis, end), wrenched), Block.UPDATE_CLIENTS);
        level.setBlock(neighborPos, there
                .setValue(connectedFor(axis, back), connect)
                .setValue(wrenchedFor(axis, back), wrenched), Block.UPDATE_CLIENTS);
    }

    private static class BarrelPlacementHelper extends PoleHelper<Direction> {
        private final boolean big;

        BarrelPlacementHelper(boolean big) {
            super(sameSize(big), s -> s.getValue(FACING).getAxis(), FACING);
            this.big = big;
        }

        private static Predicate<BlockState> sameSize(boolean big) {
            return s -> s.getBlock() instanceof BoilerBarrelBlock b && b.big == big;
        }

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem item
                    && item.getBlock() instanceof BoilerBarrelBlock b && b.big == big;
        }
    }
}
