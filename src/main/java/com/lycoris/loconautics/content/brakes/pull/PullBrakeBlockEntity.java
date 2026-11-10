package com.lycoris.loconautics.content.brakes.pull;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class PullBrakeBlockEntity extends SmartBlockEntity {

    public static final int MAX_POWER = 15;

    private static final double CHASE_SPEED = 0.35;

    public final LerpedFloat pull = LerpedFloat.linear().startWithValue(0);

    private static final int SOUND_COOLDOWN_TICKS = 6;

    private int power;

    private long lastSoundTick;

    private boolean synced;

    public PullBrakeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public int power() {
        return power;
    }

    public void setPower(int next) {
        int clamped = Mth.clamp(next, 0, MAX_POWER);
        if (clamped == power || level == null) return;
        power = clamped;
        if (level.isClientSide) {
            chase();
            return;
        }
        long now = level.getGameTime();
        if (now - lastSoundTick >= SOUND_COOLDOWN_TICKS) {
            lastSoundTick = now;
            level.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.25f,
                    0.7f + power / (float) MAX_POWER * 0.6f);
        }
        setChanged();
        notifyUpdate();
    }

    private void chase() {
        pull.chase(power / (double) MAX_POWER, CHASE_SPEED, LerpedFloat.Chaser.EXP);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) pull.tickChaser();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0, 1, 0);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Power", power);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        power = Mth.clamp(tag.getInt("Power"), 0, MAX_POWER);
        if (!clientPacket) return;
        if (synced) {
            chase();
            return;
        }
        synced = true;
        pull.startWithValue(power / (double) MAX_POWER);
    }
}
