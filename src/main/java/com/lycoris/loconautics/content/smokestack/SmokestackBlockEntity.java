package com.lycoris.loconautics.content.smokestack;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.smoke.SmokeEmitter;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.lycoris.loconautics.foundation.smoke.SmokeStyle;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmokestackBlockEntity extends BlockEntity {

    private static final String COLOR_KEY = "Color";
    private static final String POWER_KEY = "Power";

    private static final float INTERVAL_BASE = 140.0F;
    private static final float LIFT_MIN = 0.7F;
    private static final float LIFT_MAX = 1.9F;

    private int color = 0xB8BCC2;
    private int power;

    private static final int PUFF_PARTICLES = 6;
    private static final double PUFF_RADIUS = 0.2;

    public static final int BOILER_CLAIM_TIMEOUT = 60;

    private int chuffTimer;
    private int chuffJitter;
    private int boilerClaimAge;

    public SmokestackBlockEntity(BlockPos pos, BlockState state) {
        super(LoconauticsRegistries.SMOKESTACK_BE.get(), pos, state);
    }

    private boolean boilerDriven;

    private final SmokestackExhaust exhaust = new SmokestackExhaust();

    private final SmokeEmitter smoke = new SmokeEmitter();

    public void setPower(int p) {
        p = Math.max(0, Math.min(15, p));
        if (p == power) {
            return;
        }
        power = p;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getPower() {
        return power;
    }

    public void setBoilerDriven(boolean driven) {
        if (driven) boilerClaimAge = 0;
        if (driven == boilerDriven) return;
        boilerDriven = driven;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void serverTick() {
        if (!boilerDriven) return;
        if (++boilerClaimAge < BOILER_CLAIM_TIMEOUT) return;
        setBoilerDriven(false);
        if (level != null) SmokestackBlock.refreshColumn(level, worldPosition);
    }

    public boolean isBoilerDriven() {
        return boilerDriven;
    }

    public void clientTick() {
        if (power <= 0) {
            chuffTimer = 0;
            smoke.stop();
            exhaust.reset();
            return;
        }
        smoke.track(new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5));
        float strength = power / 15.0F;

        float lift = boilerDriven ? 1.0F : Mth.lerp(strength, LIFT_MIN, LIFT_MAX);
        float trailScale = 0.8F + 0.4F * strength;
        smoke.trail(level, new SmokeOptions(color, SmokeStyle.TRAIL, trailScale, lift),
                particleRate() / Math.round(Mth.lerp(strength, 4.0F, 1.0F)));

        double wheelSpeed = boilerDriven ? SmokestackExhaust.wheelSpeed(worldPosition) : 0.0;
        if (exhaust.tick(level, smoke.position(), wheelSpeed, power)) {
            chuffTimer = 0;
            puff(strength, SmokestackExhaust.pace(wheelSpeed), lift);
            return;
        }
        if (SmokestackExhaust.isMoving(wheelSpeed)) {
            return;
        }

        int interval = Math.max(2, Math.round(INTERVAL_BASE / power)) + chuffJitter;
        if (++chuffTimer >= interval) {
            chuffTimer = 0;
            chuffJitter = level.random.nextInt(5) - 2;
            if (boilerDriven) {
                exhaust.idleChuff(level, smoke.position(), power);
            }
            puff(strength, 0.0F, lift);
        }
    }

    private void puff(float strength, float pace, float lift) {
        float scale = (0.9F + 0.4F * strength) * (1.1F - 0.25F * pace);
        smoke.puff(level, new SmokeOptions(color, SmokeStyle.CHUFF, scale, lift),
                Math.max(1, Math.round(PUFF_PARTICLES * particleRate())), PUFF_RADIUS);
    }

    private static float particleRate() {
        return LoconauticsConfig.SMOKESTACK_PARTICLES_PER_TICK.get().floatValue();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int rgb) {
        int next = rgb & 0xFFFFFF;
        if (next == this.color) return;
        this.color = next;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(COLOR_KEY, color);
        tag.putInt(POWER_KEY, power);
        tag.putBoolean("BoilerDriven", boilerDriven);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(COLOR_KEY)) {
            color = tag.getInt(COLOR_KEY) & 0xFFFFFF;
        }
        if (tag.contains(POWER_KEY)) {
            power = tag.getInt(POWER_KEY);
        }
        boilerDriven = tag.getBoolean("BoilerDriven");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(COLOR_KEY, color);
        tag.putInt(POWER_KEY, power);
        tag.putBoolean("BoilerDriven", boilerDriven);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
    }
}