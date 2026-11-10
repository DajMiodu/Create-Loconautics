package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class KnuckleBlock extends HorizontalDirectionalBlock
        implements IBE<KnuckleBlockEntity>, IWrenchable, BlockSubLevelCollisionShape,
        BlockSubLevelAssemblyListener {

    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public static final MapCodec<KnuckleBlock> CODEC = simpleCodec(KnuckleBlock::new);

    private static final Map<Direction, VoxelShape> DOCKED_SHAPES =
            VoxelShapes.byHorizontalFacing(Block.box(0, 4, 13, 16, 16, 16));

    private static final double OPEN_COUPLING_YAW = -22.5;
    private static final double OPEN_PIN_LIFT = 1.0 / 16.0;

    private static final Vec3 CENTER = new Vec3(0.5, 0.0, 0.5);
    private static final Vec3 COUPLING_PIVOT_NORTH = new Vec3(13.5 / 16.0, 0.0, 3.5 / 16.0);

    private static final AABB COUPLING_UPPER_NORTH = aabb(7, 7, 7.1, 12, 13, 9.1);
    private static final AABB COUPLING_LOWER_NORTH = aabb(4.5, 6, 0, 13.5, 14, 3);
    private static final AABB PIN_UPPER_NORTH = aabb(7.2, 10.2, 7.2, 8.8, 15.8, 8.8);
    private static final AABB PIN_LOWER_NORTH = aabb(7.2, 3.7, 7.2, 8.8, 9.3, 8.8);

    private static final double COUPLER_LIFT = CopycatPlacement.CENTER.blocks();

    private static final Map<Direction, VoxelShape> CLOSED_COUPLER;
    private static final Map<Direction, VoxelShape> OPEN_COUPLER;
    private static final Map<Direction, List<AABB>> OPEN_COUPLING;
    private static final Map<Direction, Vec3> COUPLING_PIVOT;
    private static final Map<CopycatPlacement, Map<Direction, VoxelShape>> CLOSED_COLLISION;
    private static final Map<CopycatPlacement, Map<Direction, VoxelShape>> OPEN_COLLISION;

    static {
        VoxelShape body = Shapes.empty();
        body = Shapes.or(body, Block.box(6, 7.5, 10, 10, 12.5, 16));
        body = Shapes.or(body, Block.box(4, 5.5, 10, 12, 7.5, 16));
        body = Shapes.or(body, Block.box(4, 12.5, 10, 12, 14.5, 16));
        body = Shapes.or(body, Block.box(0.75736, 5.05, 4.09314, 5.70711, 14.95, 9.04289));
        body = Shapes.or(body, Block.box(0.757359, 6.025, 5.12468, 3.21197, 13.975, 9.20288));
        body = Shapes.or(body, Block.box(4, 5, 6, 12, 15, 10));
        body = Shapes.or(body, Block.box(2, 6, 7, 14, 14, 9.975));
        body = Shapes.or(body, Block.box(12, 5, 2, 15, 15, 9));

        VoxelShape closed = Shapes.or(body,
                Shapes.create(COUPLING_UPPER_NORTH), Shapes.create(COUPLING_LOWER_NORTH),
                Shapes.create(PIN_UPPER_NORTH), Shapes.create(PIN_LOWER_NORTH));
        VoxelShape open = Shapes.or(body,
                Shapes.create(PIN_UPPER_NORTH.move(0.0, OPEN_PIN_LIFT, 0.0)),
                Shapes.create(PIN_LOWER_NORTH.move(0.0, OPEN_PIN_LIFT, 0.0)));

        CLOSED_COUPLER = VoxelShapes.byHorizontalFacing(closed);
        OPEN_COUPLER = VoxelShapes.byHorizontalFacing(open);

        EnumMap<Direction, List<AABB>> coupling = new EnumMap<>(Direction.class);
        EnumMap<Direction, Vec3> pivots = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            double yaw = shapeYaw(facing);
            coupling.put(facing, List.of(spin(COUPLING_UPPER_NORTH, CENTER, yaw),
                    spin(COUPLING_LOWER_NORTH, CENTER, yaw)));
            pivots.put(facing, spin(COUPLING_PIVOT_NORTH, CENTER, yaw));
        }
        OPEN_COUPLING = Map.copyOf(coupling);
        COUPLING_PIVOT = Map.copyOf(pivots);

        Map<Direction, VoxelShape> openSolid = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape solid = OPEN_COUPLER.get(facing);
            for (AABB box : OPEN_COUPLING.get(facing)) {
                solid = Shapes.or(solid, Shapes.create(swungBounds(box, COUPLING_PIVOT.get(facing), OPEN_COUPLING_YAW)));
            }
            openSolid.put(facing, solid);
        }
        CLOSED_COLLISION = liftedByPlacement(CLOSED_COUPLER);
        OPEN_COLLISION = liftedByPlacement(openSolid);
    }

    // clipped to the cell because rapier clamps collider boxes to the unit cube
    private static Map<CopycatPlacement, Map<Direction, VoxelShape>> liftedByPlacement(Map<Direction, VoxelShape> coupler) {
        EnumMap<CopycatPlacement, Map<Direction, VoxelShape>> byPlacement = new EnumMap<>(CopycatPlacement.class);
        for (CopycatPlacement placement : CopycatPlacement.values()) {
            EnumMap<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape whole = Shapes.or(DOCKED_SHAPES.get(facing).move(0.0, placement.blocks(), 0.0),
                        coupler.get(facing).move(0.0, COUPLER_LIFT, 0.0));
                byFacing.put(facing, Shapes.join(whole, Shapes.block(), BooleanOp.AND).optimize());
            }
            byPlacement.put(placement, byFacing);
        }
        return byPlacement;
    }

    private static AABB swungBounds(AABB box, Vec3 pivot, double degrees) {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Vec3 corner : new Vec3[]{new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ)}) {
            Vec3 turned = spin(corner, pivot, degrees);
            minX = Math.min(minX, turned.x);
            maxX = Math.max(maxX, turned.x);
            minZ = Math.min(minZ, turned.z);
            maxZ = Math.max(maxZ, turned.z);
        }
        return new AABB(minX, box.minY, minZ, maxX, box.maxY, maxZ);
    }

    private static AABB aabb(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    private static double shapeYaw(Direction facing) {
        return switch (facing) {
            case EAST -> 270.0;
            case SOUTH -> 180.0;
            case WEST -> 90.0;
            default -> 0.0;
        };
    }

    private static Vec3 spin(Vec3 point, Vec3 pivot, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double dx = point.x - pivot.x;
        double dz = point.z - pivot.z;
        return new Vec3(pivot.x + dx * cos + dz * sin, point.y, pivot.z - dx * sin + dz * cos);
    }

    private static AABB spin(AABB box, Vec3 pivot, double degrees) {
        Vec3 min = spin(new Vec3(box.minX, box.minY, box.minZ), pivot, degrees);
        Vec3 max = spin(new Vec3(box.maxX, box.maxY, box.maxZ), pivot, degrees);
        return new AABB(Math.min(min.x, max.x), min.y, Math.min(min.z, max.z),
                Math.max(min.x, max.x), max.y, Math.max(min.z, max.z));
    }

    public static boolean redstoneLocked(BlockGetter level, BlockPos pos, Direction facing) {
        return level instanceof Level actual && actual.hasNeighborSignal(pos.relative(facing.getOpposite()));
    }

    public KnuckleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTED, false)
                .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.TOP));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape plate = DOCKED_SHAPES.get(facing).move(0.0, state.getValue(CopycatPlacement.PLACEMENT).blocks(), 0.0);
        if (state.getValue(CONNECTED)) {
            return plate;
        }
        if (redstoneLocked(level, pos, facing)) {
            return Shapes.or(plate, CLOSED_COUPLER.get(facing).move(0.0, COUPLER_LIFT, 0.0));
        }
        Vec3 pivot = COUPLING_PIVOT.get(facing);
        List<AABB> swung = new ArrayList<>(2);
        for (AABB box : OPEN_COUPLING.get(facing)) {
            swung.add(box.move(0.0, COUPLER_LIFT, 0.0));
        }
        return TiltedShape.of(Shapes.or(plate, OPEN_COUPLER.get(facing).move(0.0, COUPLER_LIFT, 0.0)), swung,
                point -> spin(point, pivot, OPEN_COUPLING_YAW),
                point -> spin(point, pivot, -OPEN_COUPLING_YAW));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        CopycatPlacement placement = state.getValue(CopycatPlacement.PLACEMENT);
        if (state.getValue(CONNECTED) || redstoneLocked(level, pos, facing)) {
            return CLOSED_COLLISION.get(placement).get(facing);
        }
        return OPEN_COLLISION.get(placement).get(facing);
    }

    // rapier panics on the swung and tilted knuckle geometry, so the body bakes a plain cube instead
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return Shapes.block();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED, CopycatPlacement.PLACEMENT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, ShiftPlacement.facing(context))
                .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.aimedAt(context));
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
        if (state.getValue(CONNECTED)) {
            if (!level.isClientSide()) {
                KnuckleConnectionManager.disconnect(level, context.getClickedPos());
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()
                && level.getBlockEntity(context.getClickedPos()) instanceof KnuckleBlockEntity be
                && be.isRotateLocked()) {
            return InteractionResult.SUCCESS;
        }
        return CopycatBlockLogic.cyclePlacement(state, level, context.getClickedPos());
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        InteractionResult reclaimed = CopycatBlockLogic.reclaim(state, level, pos, context.getPlayer());
        if (reclaimed != InteractionResult.PASS) {
            return reclaimed;
        }
        if (state.getValue(CONNECTED)) {
            KnuckleConnectionManager.disconnect(level, pos);
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            KnuckleConnectionManager.sever(level, pos);
            if (!movedByPiston) {
                CopycatBlockLogic.popConsumed(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    @Override
    public Class<KnuckleBlockEntity> getBlockEntityClass() {
        return KnuckleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KnuckleBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.KNUCKLE_BE.get();
    }
}