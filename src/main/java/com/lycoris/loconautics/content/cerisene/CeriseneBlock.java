package com.lycoris.loconautics.content.cerisene;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class CeriseneBlock extends HalfTransparentBlock {

    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, CeriseneTuning.MAX_HEAT);

    public static final BooleanProperty PRIMED = BooleanProperty.create("primed");

    public static final MapCodec<CeriseneBlock> CODEC = simpleCodec(CeriseneBlock::new);

    public CeriseneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HEAT, 0).setValue(PRIMED, false));
    }

    @Override
    protected MapCodec<? extends HalfTransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT, PRIMED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, CeriseneTuning.HEAT_INTERVAL_TICKS);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, CeriseneTuning.HEAT_INTERVAL_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PRIMED)) {
            CeriseneExplosion.chainTick(level, pos);
            return;
        }
        int heat = state.getValue(HEAT);
        if (heat == CeriseneTuning.MAX_HEAT && random.nextFloat() < CeriseneTuning.EXPLOSION_CHANCE) {
            CeriseneExplosion.explode(level, pos);
            return;
        }
        int next = CeriseneHeat.nextHeat(level, pos, heat);
        if (next != heat) {
            level.setBlock(pos, state.setValue(HEAT, next), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, CeriseneTuning.HEAT_INTERVAL_TICKS);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int heat = state.getValue(HEAT);
        if (heat < 3) {
            return;
        }
        int count = heat == CeriseneTuning.MAX_HEAT ? 3 : 1;
        for (int i = 0; i < count; i++) {
            if (random.nextFloat() > 0.5f) {
                continue;
            }
            Direction face = Direction.getRandom(random);
            double x = pos.getX() + 0.5 + face.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
            double y = pos.getY() + 0.5 + face.getStepY() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
            double z = pos.getZ() + 0.5 + face.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
            level.addParticle(random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.LAVA, x, y, z, 0.0, 0.03, 0.0);
        }
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return CeriseneTuning.FLAMMABILITY;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return CeriseneTuning.FIRE_SPREAD_SPEED;
    }
}
