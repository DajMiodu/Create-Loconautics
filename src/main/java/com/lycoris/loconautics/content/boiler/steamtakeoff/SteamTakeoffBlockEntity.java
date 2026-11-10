package com.lycoris.loconautics.content.boiler.steamtakeoff;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SteamTakeoffBlockEntity extends GeneratingKineticBlockEntity {

    private float rpm;
    private float boilerCapacity;
    private float regulator = 1f;

    public static final int SUPPLY_TIMEOUT = 60;

    private int sinceSupplied = SUPPLY_TIMEOUT;

    public SteamTakeoffBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void supply(float shaftRpm, float suppliedCapacity) {
        sinceSupplied = 0;
        float newRpm = Math.max(0f, shaftRpm);
        if (newRpm == rpm && suppliedCapacity == boilerCapacity) return;
        rpm = newRpm;
        boilerCapacity = suppliedCapacity;
        updateGeneratedRotation();
    }

    public void cut() {
        boolean wasZero = rpm == 0f && boilerCapacity == 0f;
        rpm = 0f;
        boilerCapacity = 0f;
        sinceSupplied = SUPPLY_TIMEOUT;
        if (!wasZero) updateGeneratedRotation();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || rpm <= 0f) return;
        if (++sinceSupplied >= SUPPLY_TIMEOUT) {
            cut();
        }
    }

    public float regulator() {
        return regulator;
    }

    public void setRegulator(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - regulator) < 0.001f) return;
        regulator = clamped;
        setChanged();
        sendData();
    }

    public float getRpm() {
        return rpm;
    }

    public float networkStressRatio() {
        if (super.capacity <= 0f || super.stress <= 0f) return 0f;
        return Math.min(1f, super.stress / super.capacity);
    }

    public boolean networkOverstressed() {
        return super.capacity > 0f && super.stress > super.capacity;
    }

    @Override
    public float getGeneratedSpeed() {
        if (rpm <= 0f) return 0f;
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE,
                getBlockState().getValue(SteamTakeoffBlock.AXIS));
        return convertToDirection(rpm, along);
    }

    @Override
    public float calculateAddedStressCapacity() {
        float perBody = (float) (double) LoconauticsConfig.SU_PER_BODY_BLOCK.get();
        float added = boilerCapacity * perBody;
        this.lastCapacityProvided = added;
        return added;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.takeoff_stats").forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.takeoff_pressure").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.number(rpm)
                .style(rpm > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        LoconauticsLang.translate("gui.goggles.takeoff_regulator").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder().text(Math.round(regulator * 100f) + "%")
                .style(regulator > 0 ? ChatFormatting.GOLD : ChatFormatting.RED)
                .forGoggles(tooltip, 1);
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Rpm", rpm);
        tag.putFloat("Capacity", boilerCapacity);
        tag.putFloat("Regulator", regulator);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        rpm = tag.getFloat("Rpm");
        boilerCapacity = tag.getFloat("Capacity");
        regulator = tag.contains("Regulator") ? tag.getFloat("Regulator") : 1f;
    }
}
