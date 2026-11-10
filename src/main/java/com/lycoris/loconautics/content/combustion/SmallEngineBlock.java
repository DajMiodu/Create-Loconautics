package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.common.DyedBlock;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.neoforge.fluids.FluidStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public class SmallEngineBlock extends HorizontalKineticBlock implements DyedBlock, IBE<SmallEngineBlockEntity> {

    private static final double PISTON_SHRINK = 0.04;

    private static final Vec3 LEFT_PIVOT = new Vec3(-0.1 / 16, 11.35 / 16, 0);

    private static final Vec3 RIGHT_PIVOT = new Vec3(16.1 / 16, 11.35 / 16, 0);

    private static final float PISTON_TILT = 45;

    private static final Map<EnginePart, Map<Direction, VoxelShape>> BODY = new EnumMap<>(EnginePart.class);

    private static final Map<EnginePart, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(EnginePart.class);

    static {
        for (EnginePart part : EnginePart.values()) {
            boolean frontFeet = part == EnginePart.SINGLE || part == EnginePart.FRONT;
            boolean rearFeet = part == EnginePart.SINGLE || part == EnginePart.BACK;
            VoxelShape body = Shapes.or(
                    Block.box(3, 2, 0, 13, 14, 16), Block.box(1, 0, 0, 15, 2, 16), Block.box(4.5, 14, 0, 11.5, 16, 16));
            if (frontFeet) body = Shapes.or(body, Block.box(0, 0, 0, 3, 4, 4), Block.box(13, 0, 0, 16, 4, 4));
            if (rearFeet) body = Shapes.or(body, Block.box(13, 0, 12, 16, 4, 16), Block.box(0, 0, 12, 3, 4, 16));
            Map<Direction, VoxelShape> bodies = VoxelShapes.byHorizontalFacing(body);
            Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape withLeft = piston(bodies.get(facing), facing, LEFT_PIVOT, -0.1, 5.9, PISTON_TILT);
                shapes.put(facing, piston(withLeft, facing, RIGHT_PIVOT, 10.1, 16.1, -PISTON_TILT));
            }
            BODY.put(part, bodies);
            SHAPES.put(part, shapes);
        }
    }

    private static VoxelShape piston(VoxelShape base, Direction facing, Vec3 pivot, double minX, double maxX,
                                     float tilt) {
        AABB box = new AABB((minX + PISTON_SHRINK) / 16, (6.35 + PISTON_SHRINK) / 16, (1 + PISTON_SHRINK) / 16,
                (maxX - PISTON_SHRINK) / 16, (11.35 - PISTON_SHRINK) / 16, (15 - PISTON_SHRINK) / 16);
        double yaw = (facing.toYRot() + 180) % 360;
        UnaryOperator<Vec3> toBlock = point -> yaw(rollAround(point, pivot, tilt), yaw);
        UnaryOperator<Vec3> toTilted = point -> rollAround(yaw(point, -yaw), pivot, -tilt);
        return TiltedShape.of(base, List.of(box), toBlock, toTilted);
    }

    private static Vec3 rollAround(Vec3 point, Vec3 pivot, double degrees) {
        double sin = Math.sin(Math.toRadians(degrees));
        double cos = Math.cos(Math.toRadians(degrees));
        double dx = point.x - pivot.x;
        double dy = point.y - pivot.y;
        return new Vec3(pivot.x + dx * cos - dy * sin, pivot.y + dx * sin + dy * cos, point.z);
    }

    private static Vec3 yaw(Vec3 point, double degrees) {
        double sin = Math.sin(Math.toRadians(degrees));
        double cos = Math.cos(Math.toRadians(degrees));
        double dx = point.x - 0.5;
        double dz = point.z - 0.5;
        return new Vec3(0.5 + dx * cos - dz * sin, point.y, 0.5 + dx * sin + dz * cos);
    }

    public static final EnumProperty<EnginePart> PART = EnumProperty.create("part", EnginePart.class);

    private final DyeColor color;

    public SmallEngineBlock(BlockBehaviour.Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
        registerDefaultState(defaultBlockState().setValue(PART, EnginePart.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    public static EnginePart placedPart(LevelAccessor level, BlockPos pos, Direction facing) {
        int behindLength = EngineRow.freeEndLength(level, pos.relative(facing.getOpposite()), facing, facing);
        int aheadLength = EngineRow.freeEndLength(level, pos.relative(facing), facing, facing.getOpposite());
        int max = SmallEngineTuning.MAX_TANDEM;
        boolean joinBehind = behindLength > 0 && behindLength < max;
        boolean joinAhead = aheadLength > 0 && (joinBehind ? behindLength + 1 : 1) + aheadLength <= max;
        return EngineRow.partOf(joinAhead, joinBehind);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !(oldState.getBlock() instanceof SmallEngineBlock)) {
            setNeighborJoins(level, pos, state, true);
        }
    }

    private static void setNeighborJoins(Level level, BlockPos pos, BlockState state, boolean joined) {
        Direction facing = state.getValue(HORIZONTAL_FACING);
        for (Direction dir : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos at = pos.relative(dir);
            if (!EngineRow.joinsToward(state, facing, dir) || !EngineRow.isEngine(level, at, facing)) continue;
            BlockState neighbor = level.getBlockState(at);
            boolean ahead = dir == facing ? EngineRow.joinsToward(neighbor, facing, facing) : joined;
            boolean behind = dir == facing ? joined : EngineRow.joinsToward(neighbor, facing, facing.getOpposite());
            EnginePart next = EngineRow.partOf(ahead, behind);
            if (next != neighbor.getValue(PART)) level.setBlock(at, neighbor.setValue(PART, next), Block.UPDATE_ALL);
        }
    }

    public static Direction output(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public DyeColor getColor() {
        return color;
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return SmallEngineBlocks.ENGINES.get(dyed).get().defaultBlockState()
                .setValue(HORIZONTAL_FACING, state.getValue(HORIZONTAL_FACING))
                .setValue(PART, state.getValue(PART));
    }

    @Override
    public void painted(ServerPlayer player, DyeColor dyed) {
        TrainAdvancements.enginePainted(player, dyed);
    }

    @Override
    public void applyPaint(Level level, BlockPos pos, BlockState painted) {
        KineticBlockEntity.switchToBlockState(level, pos, painted);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        Direction output = output(state);
        return face == output && !EngineRow.joinsToward(state, output, output);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !moved && !(newState.getBlock() instanceof SmallEngineBlock)
                && level.getBlockEntity(pos) instanceof SmallEngineBlockEntity engine) {
            engine.handOverFuel();
        }
        if (!level.isClientSide && !(newState.getBlock() instanceof SmallEngineBlock)) {
            setNeighborJoins(level, pos, state, false);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = ShiftPlacement.facing(context);
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing)
                .setValue(PART, placedPart(context.getLevel(), context.getClickedPos(), facing));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(PART)).get(state.getValue(HORIZONTAL_FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return BODY.get(state.getValue(PART)).get(state.getValue(HORIZONTAL_FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult painted = paint(stack, state, level, pos, player);
        if (painted != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return painted;

        SmallEngineBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        FluidStack before = be.getTank().getFluid().copy();
        boolean emptied = FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, be);
        boolean filled = !emptied && FluidHelper.tryFillItemFromBE(level, player, hand, stack, be);
        if (!emptied && !filled) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (!level.isClientSide) {
            SoundEvent sound = emptied ? FluidHelper.getEmptySound(be.getTank().getFluid())
                    : FluidHelper.getFillSound(before);
            if (sound != null) level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5f, 1f);
            be.notifyUpdate();
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public Class<SmallEngineBlockEntity> getBlockEntityClass() {
        return SmallEngineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SmallEngineBlockEntity> getBlockEntityType() {
        return SmallEngineBlocks.ENGINE_BE.get();
    }
}
