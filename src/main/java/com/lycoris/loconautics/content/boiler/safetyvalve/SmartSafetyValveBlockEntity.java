package com.lycoris.loconautics.content.boiler.safetyvalve;

import com.lycoris.loconautics.foundation.LoconauticsLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SmartSafetyValveBlockEntity extends SafetyValveBlockEntity {

    public static final float AUTO_THRESHOLD = 0.75f;
    public static final float DANGER_THRESHOLD = 0.9f;
    public static final float MIN_THRESHOLD = 0.05f;
    public static final float MAX_THRESHOLD = 1.0f;

    private static final float RAMP_BAND = 0.08f;
    private static final int VALVE_GRACE_TICKS = 5;

    private float threshold = AUTO_THRESHOLD;
    private boolean valveDriven;
    private long lastValveTick = Long.MIN_VALUE;

    public SmartSafetyValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected Vec3 mouthDirection() {
        return Vec3.atLowerCornerOf(getBlockState().getValue(SmartSafetyValveBlock.FACING).getNormal());
    }

    @Override
    protected double mouthDistance() {
        double lift = getBlockState().getValue(SmartSafetyValveBlock.RAISED) ? SmartSafetyValveBlock.BARREL_BULGE : 0.0;
        return 14.0 / 16.0 + lift - 0.5;
    }

    public float threshold() {
        return threshold;
    }

    public boolean isAuto() {
        return !valveDriven;
    }

    public boolean isValveDriven() {
        return valveDriven;
    }

    public boolean isDangerous() {
        return threshold >= DANGER_THRESHOLD;
    }

    public static float clampThreshold(float value) {
        return Mth.clamp(value, MIN_THRESHOLD, MAX_THRESHOLD);
    }

    public void driveFromValve(float fraction) {
        if (level != null) lastValveTick = level.getGameTime();
        if (!valveDriven) {
            valveDriven = true;
            setChanged();
            sendData();
        }
        applyThreshold(MIN_THRESHOLD + (MAX_THRESHOLD - MIN_THRESHOLD) * fraction);
    }

    private void applyThreshold(float value) {
        float clamped = clampThreshold(value);
        if (Math.abs(clamped - threshold) < 0.001f) return;
        threshold = clamped;
        setChanged();
        sendData();
    }

    public void updateFromBoiler(float pressureFraction) {
        expireValve();
        float over = pressureFraction - threshold;
        setOpening(over <= 0f ? 0f : Math.min(1f, over / RAMP_BAND));
    }

    private void expireValve() {
        if (!valveDriven || level == null) return;
        if (level.getGameTime() - lastValveTick <= VALVE_GRACE_TICKS) return;
        valveDriven = false;
        threshold = AUTO_THRESHOLD;
        setChanged();
        sendData();
    }

    private String modeKey() {
        return valveDriven ? "gui.goggles.smart_valve_mode_valve" : "gui.goggles.smart_valve_mode_auto";
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.smart_valve_stats").forGoggles(tooltip);

        LoconauticsLang.translate("gui.goggles.smart_valve_mode")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate(modeKey())
                .style(isAuto() ? ChatFormatting.AQUA : ChatFormatting.GOLD).forGoggles(tooltip, 1);

        LoconauticsLang.translate("gui.goggles.smart_valve_threshold")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder().text(Mth.floor(threshold * 100f) + "%")
                .style(isDangerous() ? ChatFormatting.RED : ChatFormatting.GOLD).forGoggles(tooltip, 1);

        LoconauticsLang.translate("gui.goggles.safety_valve_opening")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder().text(Mth.floor(opening() * 100f) + "%")
                .style(opening() <= 0f ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD)
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Threshold", threshold);
        tag.putBoolean("ValveDriven", valveDriven);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putFloat("Threshold", threshold);
        tag.putBoolean("ValveDriven", valveDriven);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        // a tag without the key used to clamp 0 up to the minimum, which vented the boiler at 5 percent
        threshold = tag.contains("Threshold") ? clampThreshold(tag.getFloat("Threshold")) : AUTO_THRESHOLD;
        valveDriven = tag.getBoolean("ValveDriven");
    }
}
