package com.lycoris.loconautics.content.ghostwheel;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

public class GhostWheelBlock extends Block implements BlockWithSubLevelCollisionCallback,
        BlockSubLevelCollisionShape {


    public static final MapCodec<GhostWheelBlock> CODEC = simpleCodec(GhostWheelBlock::new);

    public static final IntegerProperty CORNER = IntegerProperty.create("corner", 0, 3);

    public static final BooleanProperty MIDDLE = BooleanProperty.create("middle");

    public static final BooleanProperty UPSIDE_DOWN = BooleanProperty.create("upside_down");

    public static final BooleanProperty ON_MONORAIL = BooleanProperty.create("on_monorail");

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public static final IntegerProperty ALONG = IntegerProperty.create("along", 0, 2);

    private static final double END_REACH = 0.75;

    private static final double COLLISION_FLOOR = LoconauticsConstants.RAIL_HEAD_HEIGHT;

    public GhostWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CORNER, 0).setValue(MIDDLE, false)
                .setValue(UPSIDE_DOWN, false).setValue(ON_MONORAIL, false).setValue(AXIS, Direction.Axis.Z)
                .setValue(ALONG, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return GhostWheelCollisionCallback.INSTANCE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORNER, MIDDLE, UPSIDE_DOWN, ON_MONORAIL, AXIS, ALONG);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return padShape(state);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return padShape(state);
    }

    private static VoxelShape padShape(BlockState state) {
        boolean monorail = state.getValue(ON_MONORAIL);
        double floor = monorail ? LoconauticsConstants.MONORAIL_PAD_FLOOR : COLLISION_FLOOR;
        if (floor < 0.0 || floor >= 1.0) {
            LoconauticsConstants.LOGGER.warn(
                    "GhostWheel COLLISION_FLOOR {} is out of [0,1). Collision lives within ONE cell. Clamping.",
                    floor);
            floor = Math.max(0.0, Math.min(0.9999, floor));
        }
        boolean hanging = state.getValue(UPSIDE_DOWN);
        double yLo = hanging ? (monorail ? floor : 0.0) : floor;
        double yHi = hanging && !monorail ? 1.0 - floor : 1.0;
        // pls work
        double acrossLo = 0.0;
        double acrossHi = 1.0;
        if (!state.getValue(MIDDLE)) {
            boolean towardMinus = state.getValue(CORNER) == 1;
            acrossLo = towardMinus ? 0.0 : 0.5;
            acrossHi = towardMinus ? 0.5 : 1.0;
        }
        int end = state.getValue(ALONG);
        double alongLo = end == 2 ? 1.0 - END_REACH : 0.0;
        double alongHi = end == 1 ? END_REACH : 1.0;
        if (state.getValue(AXIS) == Direction.Axis.X) {
            return Shapes.box(alongLo, yLo, acrossLo, alongHi, yHi, acrossHi);
        }
        return Shapes.box(acrossLo, yLo, alongLo, acrossHi, yHi, alongHi);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, BlockState entityState,
                                     LivingEntity entity, int numberOfParticles) {
        return true;
    }

    @Override
    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        return true;
    }

    public static IClientBlockExtensions clientExtensions() {
        return new IClientBlockExtensions() {
            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
                return true;
            }

            @Override
            public boolean addHitEffects(BlockState state, Level level, net.minecraft.world.phys.HitResult target,
                                         ParticleEngine manager) {
                return true;
            }
        };
    }
}
