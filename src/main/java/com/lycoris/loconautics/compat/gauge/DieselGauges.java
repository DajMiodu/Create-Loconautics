package com.lycoris.loconautics.compat.gauge;

import com.lycoris.loconautics.compat.Reflected;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

// Create: Diesel Generators, read by reflection so the mod is not needed to build or run
public final class DieselGauges {

    private static final String PACKAGE = "com.jesz.createdieselgenerators.content.diesel_engine.";
    private static final Class<?> ENGINE = Reflected.classOf(PACKAGE + "IEngine");
    private static final Class<? extends BlockEntity> NORMAL =
            Reflected.blockEntity(PACKAGE + "normal.DieselEngineBlockEntity");
    private static final Class<? extends BlockEntity> MODULAR =
            Reflected.blockEntity(PACKAGE + "modular.ModularDieselEngineBlockEntity");
    private static final Class<? extends BlockEntity> HUGE =
            Reflected.blockEntity(PACKAGE + "huge.HugeDieselEngineBlockEntity");

    private DieselGauges() {
    }

    public static List<Class<? extends BlockEntity>> sources() {
        return Reflected.present(NORMAL, MODULAR, HUGE);
    }

    private static boolean isEngine(BlockEntity be) {
        return Reflected.is(ENGINE, be);
    }

    @Nullable
    private static FluidTank tank(BlockEntity be) {
        return isEngine(be) && Reflected.call(be, "getTank") instanceof FluidTank tank ? tank : null;
    }

    @Nullable
    public static String fuel(BlockEntity be) {
        FluidTank tank = tank(be);
        if (tank == null) return null;
        return String.format(Locale.US, "%,d / %,d mB", tank.getFluidAmount(), tank.getCapacity());
    }

    @Nullable
    public static Float fuelFraction(BlockEntity be) {
        FluidTank tank = tank(be);
        return tank == null ? null : GaugeMetric.over(tank.getFluidAmount(), tank.getCapacity());
    }

    @Nullable
    public static String fuelFill(BlockEntity be) {
        Float fraction = fuelFraction(be);
        return fraction == null ? null : GaugeMetric.pct(fraction);
    }

    @Nullable
    private static Float burnPerTick(BlockEntity be) {
        Double rate = Reflected.number(be, "getCachedBurnRate");
        Double throttle = Reflected.number(be, "getFuelThrottle");
        return rate == null || throttle == null ? null : (float) (rate * throttle);
    }

    @Nullable
    public static String burnsFor(BlockEntity be) {
        FluidTank tank = tank(be);
        Float perTick = tank == null ? null : burnPerTick(be);
        if (tank == null || perTick == null) return null;
        if (perTick <= 0f) return Component.translatable("loconautics.gui.goggles.gauge_diesel_idle").getString();
        return GaugeMetric.formatTime((int) (tank.getFluidAmount() / perTick / 20f));
    }

    @Nullable
    public static Float burnsForFraction(BlockEntity be) {
        FluidTank tank = tank(be);
        Float perTick = tank == null ? null : burnPerTick(be);
        if (tank == null || perTick == null) return null;
        return perTick <= 0f ? 0f : GaugeMetric.over(tank.getFluidAmount() / perTick / 20f, 600f);
    }

    @Nullable
    private static Float speed(BlockEntity be) {
        if (!Reflected.is(NORMAL, be) && !Reflected.is(MODULAR, be)) return null;
        Double speed = Reflected.number(be, "getGeneratedSpeed");
        return speed == null ? null : (float) Math.abs(speed);
    }

    @Nullable
    public static String rpm(BlockEntity be) {
        Float speed = speed(be);
        return speed == null ? null : String.format(Locale.US, "%,.0f RPM", speed);
    }

    @Nullable
    public static Float rpmFraction(BlockEntity be) {
        Float speed = speed(be);
        return speed == null ? null : GaugeMetric.over(speed, 256f);
    }

    @Nullable
    public static String throttle(BlockEntity be) {
        Float throttle = throttleFraction(be);
        return throttle == null ? null : GaugeMetric.pct(throttle);
    }

    @Nullable
    public static Float throttleFraction(BlockEntity be) {
        if (!isEngine(be)) return null;
        Double throttle = Reflected.number(be, "getThrottle");
        return throttle == null ? null : (float) (double) throttle;
    }
}
