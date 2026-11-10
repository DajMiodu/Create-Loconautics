package com.lycoris.loconautics.content.boiler.safetyvalve;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SmartSafetyValveBlock extends SafetyValveBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public static final BooleanProperty RAISED = BooleanProperty.create("raised");

    public static final BooleanProperty BRASS = BooleanProperty.create("brass");

    public static final double BARREL_BULGE = 0.5;

    private static final double[][] PARTS = {
            {2, 0, 2, 14, 4, 14},
            {4, 4, 4, 12, 12, 12},
            {3, 12, 3, 13, 14, 13}
    };

    private static final VoxelShape[] SHAPES = new VoxelShape[6];
    private static final VoxelShape[] SHAPES_RAISED = new VoxelShape[6];
    private static final VoxelShape[] CAPS = new VoxelShape[6];
    private static final VoxelShape[] CAPS_RAISED = new VoxelShape[6];

    static {
        for (Direction dir : Direction.values()) {
            SHAPES[dir.get3DDataValue()] = buildShape(dir, 0.0);
            SHAPES_RAISED[dir.get3DDataValue()] = buildShape(dir, BARREL_BULGE);
            CAPS[dir.get3DDataValue()] = buildCap(dir, 0.0);
            CAPS_RAISED[dir.get3DDataValue()] = buildCap(dir, BARREL_BULGE);
        }
    }

    public SmartSafetyValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(RAISED, false).setValue(BRASS, false));
    }

    private static VoxelShape buildShape(Direction facing, double lift) {
        VoxelShape shape = Shapes.empty();
        for (double[] p : PARTS) {
            shape = Shapes.or(shape, partShape(facing, lift, p));
        }
        return shape;
    }

    private static VoxelShape buildCap(Direction facing, double lift) {
        return partShape(facing, lift, PARTS[PARTS.length - 1]);
    }

    private static VoxelShape partShape(Direction facing, double lift, double[] p) {
        double[] a = orient(facing, p[0] / 16.0, p[1] / 16.0 + lift, p[2] / 16.0);
        double[] b = orient(facing, p[3] / 16.0, p[4] / 16.0 + lift, p[5] / 16.0);
        return Shapes.box(
                Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }

    private static double[] orient(Direction facing, double x, double y, double z) {
        return switch (facing) {
            case DOWN -> new double[]{x, 1 - y, z};
            case NORTH -> new double[]{x, z, 1 - y};
            case SOUTH -> new double[]{1 - x, z, y};
            case EAST -> new double[]{y, z, x};
            case WEST -> new double[]{1 - y, z, 1 - x};
            default -> new double[]{x, y, z};
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RAISED, BRASS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockPos base = context.getClickedPos().relative(face.getOpposite());
        boolean onBarrel = context.getLevel().getBlockState(base).getBlock() instanceof BoilerBarrelBlock;
        return defaultBlockState().setValue(FACING, face).setValue(RAISED, onBarrel);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        int face = state.getValue(FACING).get3DDataValue();
        VoxelShape cap = state.getValue(RAISED) ? CAPS_RAISED[face] : CAPS[face];
        BlockPos pos = context.getClickedPos();
        Vec3 local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if (!cap.bounds().inflate(1.0E-3).contains(local)) return super.onWrenched(state, context);

        Level level = context.getLevel();
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(BRASS), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.8f, 1.1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int face = state.getValue(FACING).get3DDataValue();
        return state.getValue(RAISED) ? SHAPES_RAISED[face] : SHAPES[face];
    }

    @Override
    public BlockEntityType<? extends SafetyValveBlockEntity> getBlockEntityType() {
        return BoilerBlocks.SMART_SAFETY_VALVE_BE.get();
    }
}
